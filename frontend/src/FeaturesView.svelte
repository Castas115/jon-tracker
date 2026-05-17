<script lang="ts">
  import { onMount } from 'svelte';
  import { api, type FeatureRequest, type FeatureStatus } from './lib/api';

  let features = $state<FeatureRequest[]>([]);
  let selectedId = $state<number | null>(null);
  let statusFilter = $state<FeatureStatus | 'all'>('all');

  let editTitle = $state('');
  let editDescription = $state('');
  let editStatus = $state<FeatureStatus>('open');
  let editing = $state(false);

  const STATUS_ORDER: Record<FeatureStatus, number> = {
    open: 0,
    in_progress: 1,
    done: 2,
    rejected: 3
  };

  const visible = $derived(
    features
      .filter((f) => statusFilter === 'all' || f.status === statusFilter)
      .sort((a, b) => STATUS_ORDER[a.status] - STATUS_ORDER[b.status])
  );
  const selected = $derived(features.find((f) => f.id === selectedId) ?? null);

  async function load() {
    features = await api.listFeatures();
  }

  onMount(() => {
    let stopped = false;
    const tick = async () => {
      try {
        const next = await api.listFeatures();
        if (!stopped) features = next;
      } catch {
        // ignore
      }
    };
    const timer = setInterval(tick, 8000);
    tick();
    return () => {
      stopped = true;
      clearInterval(timer);
    };
  });

  function openEdit(f: FeatureRequest) {
    selectedId = f.id;
    editTitle = f.title;
    editDescription = f.description ?? '';
    editStatus = f.status;
    editing = false;
  }

  async function saveEdit() {
    if (!selected) return;
    const updated = await api.updateFeature(selected.id, {
      title: editTitle.trim(),
      description: editDescription.trim() || null,
      status: editStatus
    });
    features = features.map((f) => (f.id === updated.id ? updated : f));
    editing = false;
  }

  async function removeFeature() {
    if (!selected) return;
    if (!confirm(`Delete feature "${selected.title}"?`)) return;
    await api.deleteFeature(selected.id);
    features = features.filter((f) => f.id !== selected!.id);
    selectedId = null;
  }

  function fmtDate(iso: string): string {
    return new Date(iso).toISOString().slice(0, 10);
  }
</script>

<section class="features">
  <aside class="list">
    <div class="filter">
      <button class:on={statusFilter === 'all'} onclick={() => (statusFilter = 'all')} type="button">All</button>
      <button class:on={statusFilter === 'open'} onclick={() => (statusFilter = 'open')} type="button">Open</button>
      <button class:on={statusFilter === 'in_progress'} onclick={() => (statusFilter = 'in_progress')} type="button">In progress</button>
      <button class:on={statusFilter === 'done'} onclick={() => (statusFilter = 'done')} type="button">Done</button>
      <button class:on={statusFilter === 'rejected'} onclick={() => (statusFilter = 'rejected')} type="button">Rejected</button>
    </div>

    {#if visible.length === 0}
      <p class="empty">No feature requests yet.</p>
    {:else}
      <ul>
        {#each visible as f (f.id)}
          <li>
            <button
              class="item"
              class:active={selectedId === f.id}
              type="button"
              onclick={() => openEdit(f)}
            >
              <div class="row">
                <span class="num">#{f.id}</span>
                <span class="title">{f.title}</span>
                <span class="status status-{f.status}">{f.status.replace('_', ' ')}</span>
              </div>
              <span class="meta">{fmtDate(f.updated_at)}{f.source_idea_id ? ` · idea #${f.source_idea_id}` : ''}</span>
            </button>
          </li>
        {/each}
      </ul>
    {/if}
  </aside>

  <div class="detail">
    {#if selected}
      <header>
        <span class="d-num">#{selected.id}</span>
        {#if editing}
          <input class="title-input" bind:value={editTitle} type="text" />
        {:else}
          <span class="d-title">{selected.title}</span>
        {/if}
        <div class="actions">
          {#if !editing}
            <button type="button" onclick={() => (editing = true)}>Edit</button>
          {:else}
            <button type="button" class="primary" onclick={saveEdit}>Save</button>
            <button type="button" onclick={() => openEdit(selected)}>Cancel</button>
          {/if}
          <button type="button" class="danger" onclick={removeFeature}>Delete</button>
        </div>
      </header>

      <div class="status-row">
        {#if editing}
          <select bind:value={editStatus}>
            <option value="open">Open</option>
            <option value="in_progress">In progress</option>
            <option value="done">Done</option>
            <option value="rejected">Rejected</option>
          </select>
        {:else}
          <span class="status status-{selected.status}">{selected.status.replace('_', ' ')}</span>
        {/if}
        <span class="meta">
          Created {fmtDate(selected.created_at)} · updated {fmtDate(selected.updated_at)}
          {#if selected.source_idea_id} · from idea #{selected.source_idea_id}{/if}
        </span>
      </div>

      <div class="description-wrap">
        {#if editing}
          <textarea bind:value={editDescription} rows="14" placeholder="Markdown description"></textarea>
        {:else if selected.description}
          <pre class="description">{selected.description}</pre>
        {:else}
          <p class="empty">No description.</p>
        {/if}
      </div>
    {:else}
      <p class="empty">Select a feature request.</p>
    {/if}
  </div>
</section>

<style>
  .features {
    display: grid;
    grid-template-columns: 360px 1fr;
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
  .filter {
    display: flex;
    gap: 0.3rem;
    flex-wrap: wrap;
  }
  .filter button {
    background: var(--bg-3);
    border: 1px solid var(--border);
    color: var(--fg-muted);
    padding: 0.3rem 0.6rem;
    border-radius: 14px;
    cursor: pointer;
    font: inherit;
    font-size: 0.75rem;
  }
  .filter button.on {
    background: var(--accent);
    color: var(--accent-fg);
    border-color: var(--accent);
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
  .row { display: flex; align-items: center; gap: 0.4rem; }
  .num { color: var(--fg-muted); font-variant-numeric: tabular-nums; font-size: 0.78rem; }
  .title {
    flex: 1;
    font-weight: 500;
    font-size: 0.9rem;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .meta { font-size: 0.7rem; color: var(--fg-muted); }
  .status {
    font-size: 0.65rem;
    text-transform: uppercase;
    letter-spacing: 0.06em;
    padding: 1px 6px;
    border-radius: 999px;
    background: var(--bg-3);
    color: var(--fg-muted);
  }
  .status-open { background: color-mix(in srgb, var(--accent) 30%, transparent); color: var(--fg); }
  .status-in_progress { background: color-mix(in srgb, #d4a017 40%, transparent); color: var(--fg); }
  .status-done { background: color-mix(in srgb, #4caf50 30%, transparent); color: var(--fg); }
  .status-rejected { background: color-mix(in srgb, var(--danger) 25%, transparent); color: var(--fg); }

  .detail {
    display: flex;
    flex-direction: column;
    gap: 0.5rem;
    background: var(--bg-2);
    border: 1px solid var(--border);
    border-radius: 8px;
    padding: 0.85rem;
    min-width: 0;
    min-height: 0;
    overflow-y: auto;
  }
  .detail header {
    display: flex;
    align-items: center;
    gap: 0.5rem;
  }
  .d-num { color: var(--fg-muted); font-variant-numeric: tabular-nums; }
  .d-title { flex: 1; font-weight: 600; font-size: 1.05rem; }
  .title-input {
    flex: 1;
    background: var(--bg);
    color: var(--fg);
    border: 1px solid var(--border);
    border-radius: 6px;
    padding: 0.3rem 0.5rem;
    font: inherit;
    font-weight: 600;
  }
  .actions { display: flex; gap: 0.35rem; }
  .actions button {
    background: var(--bg-3);
    border: 1px solid var(--border);
    color: var(--fg);
    padding: 0.3rem 0.65rem;
    border-radius: 6px;
    cursor: pointer;
    font: inherit;
    font-size: 0.78rem;
  }
  .actions button.primary {
    background: var(--accent);
    color: var(--accent-fg);
    border-color: var(--accent);
    font-weight: 600;
  }
  .actions button.danger {
    color: var(--danger);
    border-color: var(--danger);
  }

  .status-row {
    display: flex;
    align-items: center;
    gap: 0.65rem;
  }
  .status-row select {
    background: var(--bg);
    color: var(--fg);
    border: 1px solid var(--border);
    border-radius: 6px;
    padding: 0.2rem 0.4rem;
    font: inherit;
    font-size: 0.85rem;
  }

  .description-wrap { flex: 1; }
  .description {
    background: var(--bg);
    border: 1px solid var(--border);
    border-radius: 8px;
    padding: 0.8rem;
    margin: 0;
    white-space: pre-wrap;
    word-break: break-word;
    font: inherit;
    font-size: 0.9rem;
  }
  textarea {
    width: 100%;
    background: var(--bg);
    color: var(--fg);
    border: 1px solid var(--border);
    border-radius: 6px;
    padding: 0.4rem 0.5rem;
    font: inherit;
    font-size: 0.9rem;
    resize: vertical;
  }
  .empty { color: var(--fg-muted); font-size: 0.9rem; }

  @media (max-width: 800px) {
    .features { grid-template-columns: 1fr; }
  }
</style>
