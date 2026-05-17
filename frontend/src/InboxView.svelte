<script lang="ts">
  import { onMount } from 'svelte';
  import { api, type Idea, type IdeaKind, type IdeaStatus } from './lib/api';

  type Props = {
    ideas: Idea[];
    onChange: (next: Idea[]) => void;
  };

  const { ideas, onChange }: Props = $props();

  // Poll the worker's responses while this view is mounted.
  onMount(() => {
    let stopped = false;
    const tick = async () => {
      try {
        const next = await api.listIdeas();
        if (!stopped) onChange(next);
      } catch {
        // ignore — keep polling
      }
    };
    const timer = setInterval(tick, 5000);
    tick();
    return () => {
      stopped = true;
      clearInterval(timer);
    };
  });

  let selectedId = $state<number | null>(null);
  let draft = $state('');
  let composeOpen = $state(false);
  let newTranscript = $state('');
  let newKind = $state<IdeaKind>('unknown');

  const selected = $derived(ideas.find((i) => i.id === selectedId) ?? null);

  async function createIdea() {
    const text = newTranscript.trim();
    if (!text) return;
    const created = await api.createIdea({ transcript: text, kind: newKind });
    onChange([created, ...ideas.filter((i) => i.id !== created.id)]);
    newTranscript = '';
    newKind = 'unknown';
    composeOpen = false;
    selectedId = created.id;
  }

  async function send() {
    if (!selected || !draft.trim()) return;
    const text = draft.trim();
    draft = '';
    const updated = await api.postIdeaMessage(selected.id, { role: 'user', text });
    onChange(ideas.map((i) => (i.id === updated.id ? updated : i)));
  }

  async function setKind(kind: IdeaKind) {
    if (!selected) return;
    const updated = await api.updateIdea(selected.id, { kind });
    onChange(ideas.map((i) => (i.id === updated.id ? updated : i)));
  }
  async function setStatus(status: IdeaStatus) {
    if (!selected) return;
    const updated = await api.updateIdea(selected.id, { status });
    onChange(ideas.map((i) => (i.id === updated.id ? updated : i)));
  }
  async function removeIdea() {
    if (!selected) return;
    if (!confirm(`Delete "${selected.title || 'idea'}"?`)) return;
    await api.deleteIdea(selected.id);
    onChange(ideas.filter((i) => i.id !== selected!.id));
    selectedId = null;
  }

  function kindColor(k: IdeaKind): string {
    if (k === 'task') return 'task';
    if (k === 'feature') return 'feature';
    return 'unknown';
  }
</script>

<section class="inbox">
  <aside class="list">
    <div class="list-head">
      <strong>Ideas</strong>
      <button class="compose" type="button" onclick={() => (composeOpen = !composeOpen)}>
        {composeOpen ? '×' : '+'}
      </button>
    </div>

    {#if composeOpen}
      <div class="compose-card">
        <textarea
          bind:value={newTranscript}
          placeholder="What's on your mind?"
          rows="3"
        ></textarea>
        <div class="kind-row">
          <label><input type="radio" name="newkind" checked={newKind === 'unknown'} onchange={() => (newKind = 'unknown')} /> AI decides</label>
          <label><input type="radio" name="newkind" checked={newKind === 'task'} onchange={() => (newKind = 'task')} /> Task</label>
          <label><input type="radio" name="newkind" checked={newKind === 'feature'} onchange={() => (newKind = 'feature')} /> Feature</label>
        </div>
        <button class="primary" type="button" onclick={createIdea} disabled={!newTranscript.trim()}>Save</button>
      </div>
    {/if}

    {#if ideas.length === 0}
      <p class="empty">Nothing yet.</p>
    {:else}
      <ul>
        {#each ideas as i (i.id)}
          {@const last = i.messages[i.messages.length - 1]}
          <li>
            <button class="item" class:active={selectedId === i.id} class:needs-info={i.status === 'needs_info'} type="button" onclick={() => (selectedId = i.id)}>
              <div class="row">
                <span class="title">{i.title || '(untitled)'}</span>
                <span class="kind kind-{kindColor(i.kind)}">{i.kind}</span>
              </div>
              {#if last}
                <span class="preview">{last.role === 'assistant' ? 'AI: ' : ''}{last.text.slice(0, 120)}</span>
              {/if}
              <span class="status">{i.status}</span>
            </button>
          </li>
        {/each}
      </ul>
    {/if}
  </aside>

  <div class="thread">
    {#if selected}
      <header>
        <span class="thread-title">{selected.title || '(untitled)'}</span>
        <div class="thread-actions">
          <button type="button" onclick={() => setKind('task')} class:on={selected.kind === 'task'}>Task</button>
          <button type="button" onclick={() => setKind('feature')} class:on={selected.kind === 'feature'}>Feature</button>
          <button type="button" onclick={() => setStatus(selected.status === 'done' ? 'new' : 'done')}>
            {selected.status === 'done' ? 'Reopen' : 'Done'}
          </button>
          <button type="button" class="danger" onclick={removeIdea}>Delete</button>
        </div>
      </header>

      <div class="messages">
        {#each selected.messages as m (m.id)}
          <div class="bubble" class:mine={m.role === 'user'}>
            {m.text}
          </div>
        {/each}
      </div>

      <form
        class="reply"
        onsubmit={(e) => {
          e.preventDefault();
          send();
        }}
      >
        <textarea bind:value={draft} placeholder="Reply…" rows="2"></textarea>
        <button class="primary" type="submit" disabled={!draft.trim()}>Send</button>
      </form>
    {:else}
      <p class="empty">Pick an idea from the list.</p>
    {/if}
  </div>
</section>

<style>
  .inbox {
    display: grid;
    grid-template-columns: 320px 1fr;
    gap: 0.75rem;
    flex: 1;
    min-height: 0;
  }
  .list {
    display: flex;
    flex-direction: column;
    gap: 0.5rem;
    overflow-y: auto;
    background: var(--bg-2);
    border: 1px solid var(--border);
    border-radius: 8px;
    padding: 0.6rem;
    min-width: 0;
  }
  .list-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }
  .compose {
    background: var(--bg-3);
    border: 1px solid var(--border);
    border-radius: 6px;
    color: var(--fg);
    width: 28px;
    height: 28px;
    cursor: pointer;
    font: inherit;
    font-size: 1.1rem;
    line-height: 1;
  }
  .compose-card {
    background: var(--bg);
    border: 1px solid var(--border);
    border-radius: 8px;
    padding: 0.5rem;
    display: flex;
    flex-direction: column;
    gap: 0.4rem;
  }
  .compose-card textarea {
    width: 100%;
    background: var(--bg-2);
    color: var(--fg);
    border: 1px solid var(--border);
    border-radius: 6px;
    padding: 0.4rem 0.5rem;
    font: inherit;
    resize: vertical;
  }
  .kind-row {
    display: flex;
    gap: 0.5rem;
    font-size: 0.78rem;
    color: var(--fg-muted);
  }
  ul {
    list-style: none;
    padding: 0;
    margin: 0;
    display: flex;
    flex-direction: column;
    gap: 0.35rem;
  }
  .item {
    width: 100%;
    text-align: left;
    background: var(--bg);
    border: 1px solid var(--border);
    border-radius: 6px;
    color: var(--fg);
    padding: 0.5rem 0.6rem;
    cursor: pointer;
    display: flex;
    flex-direction: column;
    gap: 0.2rem;
    font: inherit;
  }
  .item:hover { background: var(--bg-3); }
  .item.active {
    border-color: var(--accent);
    background: color-mix(in srgb, var(--accent) 12%, var(--bg));
  }
  .item.needs-info { border-color: var(--accent); }
  .row { display: flex; align-items: center; gap: 0.4rem; }
  .title { flex: 1; font-weight: 600; font-size: 0.9rem; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  .kind {
    font-size: 0.65rem;
    text-transform: uppercase;
    letter-spacing: 0.06em;
    padding: 1px 6px;
    border-radius: 999px;
    background: var(--bg-3);
    color: var(--fg-muted);
  }
  .kind-task { background: color-mix(in srgb, var(--accent) 30%, transparent); color: var(--fg); }
  .kind-feature { background: color-mix(in srgb, #d4a017 40%, transparent); color: var(--fg); }
  .preview {
    font-size: 0.75rem;
    color: var(--fg-muted);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .status {
    font-size: 0.65rem;
    color: var(--fg-muted);
    text-transform: uppercase;
    letter-spacing: 0.05em;
  }

  .thread {
    display: flex;
    flex-direction: column;
    gap: 0.5rem;
    background: var(--bg-2);
    border: 1px solid var(--border);
    border-radius: 8px;
    padding: 0.75rem;
    min-width: 0;
    min-height: 0;
  }
  .thread header {
    display: flex;
    align-items: center;
    gap: 0.5rem;
  }
  .thread-title { flex: 1; font-weight: 600; }
  .thread-actions { display: flex; gap: 0.35rem; }
  .thread-actions button {
    background: var(--bg-3);
    border: 1px solid var(--border);
    color: var(--fg);
    padding: 0.3rem 0.55rem;
    border-radius: 6px;
    cursor: pointer;
    font: inherit;
    font-size: 0.78rem;
  }
  .thread-actions button.on {
    background: var(--accent);
    color: var(--accent-fg);
    border-color: var(--accent);
  }
  .thread-actions .danger { color: var(--danger); border-color: var(--danger); }

  .messages {
    flex: 1;
    overflow-y: auto;
    display: flex;
    flex-direction: column;
    gap: 0.4rem;
    padding: 0.25rem 0;
  }
  .bubble {
    align-self: flex-start;
    max-width: 80%;
    background: var(--bg-3);
    border-radius: 10px;
    padding: 0.45rem 0.65rem;
    font-size: 0.9rem;
    white-space: pre-wrap;
  }
  .bubble.mine {
    align-self: flex-end;
    background: color-mix(in srgb, var(--accent) 35%, var(--bg-3));
  }

  .reply {
    display: flex;
    gap: 0.4rem;
    align-items: flex-end;
  }
  .reply textarea {
    flex: 1;
    background: var(--bg);
    color: var(--fg);
    border: 1px solid var(--border);
    border-radius: 6px;
    padding: 0.4rem 0.5rem;
    font: inherit;
    resize: vertical;
  }
  button.primary {
    background: var(--accent);
    color: var(--accent-fg);
    border: 1px solid var(--accent);
    padding: 0.45rem 0.85rem;
    border-radius: 6px;
    cursor: pointer;
    font: inherit;
    font-weight: 600;
  }
  button.primary:disabled { opacity: 0.5; cursor: default; }

  .empty {
    color: var(--fg-muted);
    font-size: 0.9rem;
    margin: 0.5rem;
  }

  @media (max-width: 800px) {
    .inbox { grid-template-columns: 1fr; }
  }
</style>
