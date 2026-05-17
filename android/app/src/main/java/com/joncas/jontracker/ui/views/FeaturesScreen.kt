package com.joncas.jontracker.ui.views

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.joncas.jontracker.api.FeatureRequest
import com.joncas.jontracker.api.FeatureRequestPayload
import com.joncas.jontracker.data.FeatureRepo
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val STATUS_OPTIONS = listOf("open", "in_progress", "done", "rejected")

@Composable
fun FeaturesScreen() {
    val features by FeatureRepo.features.collectAsState()
    var selectedId by remember { mutableStateOf<Int?>(null) }
    var filter by remember { mutableStateOf("all") }

    LaunchedEffect(Unit) {
        while (true) {
            FeatureRepo.refresh()
            delay(8_000)
        }
    }

    val current = selectedId?.let { id -> features.firstOrNull { it.id == id } }
    if (current != null) {
        FeatureDetail(current, onBack = { selectedId = null })
    } else {
        FeatureList(
            features = features,
            filter = filter,
            onFilterChange = { filter = it },
            onOpen = { selectedId = it.id },
        )
    }
}

@Composable
private fun FeatureList(
    features: List<FeatureRequest>,
    filter: String,
    onFilterChange: (String) -> Unit,
    onOpen: (FeatureRequest) -> Unit,
) {
    val visible = features
        .filter { filter == "all" || it.status == filter }
        .sortedBy { STATUS_OPTIONS.indexOf(it.status).coerceAtLeast(99) }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ChipFilter("All", filter == "all") { onFilterChange("all") }
            STATUS_OPTIONS.forEach { s ->
                ChipFilter(s.replace('_', ' '), filter == s) { onFilterChange(s) }
            }
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
        if (visible.isEmpty()) {
            Text(
                "No feature requests yet. Talk to the assistant about app changes — it'll open tickets for you.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(12.dp),
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(items = visible, key = { it.id }) { fr ->
                    FeatureRow(fr, onClick = { onOpen(fr) })
                }
            }
        }
    }
}

@Composable
private fun ChipFilter(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}

@Composable
private fun FeatureRow(fr: FeatureRequest, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "#${fr.id}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Box(modifier = Modifier.padding(horizontal = 6.dp))
                Text(
                    fr.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
            }
            fr.description?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it.lines().firstOrNull()?.take(140) ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            StatusBadge(fr.status)
        }
    }
}

@Composable
private fun StatusBadge(status: String) {
    val color = when (status) {
        "open" -> MaterialTheme.colorScheme.primary
        "in_progress" -> Color(0xFFD4A017)
        "done" -> Color(0xFF4CAF50)
        "rejected" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    AssistChip(
        onClick = {},
        label = { Text(status.replace('_', ' '), style = MaterialTheme.typography.labelSmall) },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = color.copy(alpha = 0.18f),
            labelColor = MaterialTheme.colorScheme.onSurface,
        ),
    )
}

@Composable
private fun FeatureDetail(fr: FeatureRequest, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var editing by remember(fr.id) { mutableStateOf(false) }
    var title by remember(fr.id) { mutableStateOf(fr.title) }
    var description by remember(fr.id) { mutableStateOf(fr.description ?: "") }
    var status by remember(fr.id) { mutableStateOf(fr.status) }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                "#${fr.id}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 6.dp),
            )
            Text(
                if (!editing) fr.title else "Editing",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            IconButton(onClick = {
                scope.launch {
                    FeatureRepo.remove(fr.id)
                    onBack()
                }
            }) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete")
            }
        }

        Row(modifier = Modifier.padding(vertical = 6.dp)) {
            STATUS_OPTIONS.forEach { s ->
                FilterChip(
                    selected = status == s,
                    onClick = {
                        status = s
                        scope.launch {
                            FeatureRepo.update(fr.id, FeatureRequestPayload(title = fr.title, status = s))
                        }
                    },
                    label = { Text(s.replace('_', ' '), style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.padding(end = 4.dp),
                )
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))

        if (editing) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Box(modifier = Modifier.padding(vertical = 4.dp))
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description (Markdown)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 6,
                maxLines = 20,
            )
            Row(modifier = Modifier.padding(top = 8.dp)) {
                TextButton(onClick = {
                    title = fr.title
                    description = fr.description ?: ""
                    editing = false
                }) { Text("Cancel") }
                Button(onClick = {
                    scope.launch {
                        FeatureRepo.update(
                            fr.id,
                            FeatureRequestPayload(
                                title = title.trim(),
                                description = description.trim().ifBlank { null },
                                status = status,
                            ),
                        )
                        editing = false
                    }
                }) { Text("Save") }
            }
        } else {
            Text(
                fr.description?.takeIf { it.isNotBlank() } ?: "No description.",
                style = MaterialTheme.typography.bodyMedium,
                color = if (fr.description.isNullOrBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            Row(modifier = Modifier.padding(top = 12.dp)) {
                Button(onClick = { editing = true }) { Text("Edit") }
            }
            fr.source_idea_id?.let {
                Text(
                    "Source idea #$it",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}
