package com.joncas.jontracker.ui.views

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.joncas.jontracker.api.Idea
import com.joncas.jontracker.api.IdeaMessage
import com.joncas.jontracker.api.IdeaMessageCreate
import com.joncas.jontracker.api.IdeaUpdate
import com.joncas.jontracker.data.IdeaRepo
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun InboxScreen() {
    val ideas by IdeaRepo.ideas.collectAsState()
    val pendingSelection by IdeaRepo.pendingSelection.collectAsState()
    var selected by remember { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()

    // Honor a programmatic selection request (e.g. just-recorded idea).
    LaunchedEffect(pendingSelection) {
        pendingSelection?.let {
            selected = it
            IdeaRepo.clearPendingSelection()
        }
    }

    // Auto-poll while this tab is on screen so the worker's replies show up
    // without forcing a manual reload.
    LaunchedEffect(Unit) {
        while (true) {
            IdeaRepo.refresh()
            delay(5_000)
        }
    }

    val current = selected?.let { id -> ideas.firstOrNull { it.id == id } }

    if (current != null) {
        ThreadView(
            idea = current,
            onBack = { selected = null },
            onSend = { text ->
                scope.launch {
                    IdeaRepo.postMessage(current.id, IdeaMessageCreate("user", text))
                }
            },
            onSetKind = { kind ->
                scope.launch { IdeaRepo.update(current.id, IdeaUpdate(kind = kind)) }
            },
            onSetStatus = { status ->
                scope.launch { IdeaRepo.update(current.id, IdeaUpdate(status = status)) }
            },
            onDelete = {
                scope.launch {
                    IdeaRepo.remove(current.id)
                    selected = null
                }
            },
        )
    } else {
        IdeaList(ideas = ideas, onOpen = { selected = it.id })
    }
}

@Composable
private fun IdeaList(ideas: List<Idea>, onOpen: (Idea) -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 8.dp)) {
        if (ideas.isEmpty()) {
            Text(
                "Nothing yet. Tap the mic icon to capture an idea.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(12.dp),
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(items = ideas, key = { it.id }) { idea -> IdeaRow(idea, onOpen) }
            }
        }
    }
}

@Composable
private fun IdeaRow(idea: Idea, onOpen: (Idea) -> Unit) {
    val needsInfo = idea.status == "needs_info"
    val unreadColor = if (needsInfo) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.surfaceVariant
    val lastMessage = idea.messages.lastOrNull()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(unreadColor.copy(alpha = 0.5f))
            .clickable { onOpen(idea) }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (idea.title.isNotBlank()) idea.title else "(untitled)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                KindBadge(idea.kind)
            }
            if (lastMessage != null) {
                Text(
                    "${if (lastMessage.role == "assistant") "AI: " else ""}${lastMessage.text.take(120)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                idea.status,
                style = MaterialTheme.typography.labelSmall,
                color = if (needsInfo) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun KindBadge(kind: String) {
    AssistChip(
        onClick = {},
        label = { Text(kind, style = MaterialTheme.typography.labelSmall) },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = when (kind) {
                "task" -> MaterialTheme.colorScheme.secondaryContainer
                "feature" -> MaterialTheme.colorScheme.tertiaryContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        ),
    )
}

@Composable
private fun ThreadView(
    idea: Idea,
    onBack: () -> Unit,
    onSend: (String) -> Unit,
    onSetKind: (String) -> Unit,
    onSetStatus: (String) -> Unit,
    onDelete: () -> Unit,
) {
    var draft by remember(idea.id) { mutableStateOf("") }
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (idea.title.isNotBlank()) idea.title else "(untitled)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    idea.status,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete")
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            AssistChip(
                onClick = { onSetKind("task") },
                label = { Text("Task") },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = if (idea.kind == "task") MaterialTheme.colorScheme.secondaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant
                ),
            )
            AssistChip(
                onClick = { onSetKind("feature") },
                label = { Text("Feature") },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = if (idea.kind == "feature") MaterialTheme.colorScheme.tertiaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant
                ),
            )
            AssistChip(
                onClick = { onSetStatus(if (idea.status == "done") "new" else "done") },
                label = { Text(if (idea.status == "done") "Reopen" else "Done") },
            )
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
        LazyColumn(
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(items = idea.messages, key = { it.id }) { msg -> MessageBubble(msg) }
        }
        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Reply…") },
                minLines = 1,
                maxLines = 4,
            )
            IconButton(
                enabled = draft.isNotBlank(),
                onClick = {
                    val text = draft.trim()
                    draft = ""
                    onSend(text)
                },
            ) {
                Icon(Icons.Filled.Send, contentDescription = "Send")
            }
        }
    }
}

@Composable
private fun MessageBubble(msg: IdeaMessage) {
    val mine = msg.role == "user"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (mine) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant
                )
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Text(msg.text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
