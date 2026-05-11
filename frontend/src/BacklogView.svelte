<script lang="ts">
  import { ymd } from './lib/dates';
  import type { Task } from './lib/types';

  type Props = {
    tasks: Task[];
    onToggle: (task: Task, dateYMD: string) => void;
    onAssignDate: (task: Task, dateYMD: string) => void;
    onRemove: (task: Task) => void;
    onCreate: () => void;
  };

  const { tasks, onToggle, onAssignDate, onRemove, onCreate }: Props = $props();

  // Backlog = singles with no fixed_date AND no completion yet (done items
  // graduate out of view, per the design).
  const items = $derived(
    tasks
      .filter(
        (t) =>
          t.task_type === 'single' &&
          t.fixed_date === null &&
          t.is_todo &&
          t.completed_dates.length === 0
      )
      .sort((a, b) => b.created_at.localeCompare(a.created_at))
  );

  function handleDate(t: Task, value: string) {
    if (!value) return;
    onAssignDate(t, value);
  }

  function complete(t: Task) {
    onToggle(t, ymd(new Date()));
  }
</script>

<section class="backlog">
  <header>
    <h2>Backlog <span class="count">{items.length}</span></h2>
    <button class="primary" type="button" onclick={onCreate}>+ Add</button>
  </header>

  {#if items.length === 0}
    <p class="empty">Nothing pending. Create undated singles to populate this list.</p>
  {:else}
    <ul>
      {#each items as t (t.id)}
        <li>
          <button
            class="check"
            type="button"
            aria-label="Mark done"
            title="Mark done (completion date = today)"
            onclick={() => complete(t)}
          ></button>
          <span class="title">{t.title}</span>
          <label class="date-pick" title="Assign a date → moves to the calendar">
            <span class="lbl">Schedule</span>
            <input
              type="date"
              onchange={(e) => handleDate(t, (e.currentTarget as HTMLInputElement).value)}
            />
          </label>
          <button
            class="del"
            type="button"
            aria-label="Delete"
            title="Delete"
            onclick={() => onRemove(t)}
          >×</button>
        </li>
      {/each}
    </ul>
  {/if}
</section>

<style>
  .backlog {
    display: flex;
    flex-direction: column;
    gap: 0.75rem;
    max-width: 720px;
    margin: 0 auto;
  }

  header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 0.75rem;
  }

  h2 {
    margin: 0;
    font-size: 1.1rem;
    font-weight: 600;
    display: flex;
    align-items: center;
    gap: 0.5rem;
  }
  .count {
    background: var(--bg-3);
    color: var(--fg-muted);
    border-radius: 999px;
    padding: 1px 8px;
    font-size: 0.75rem;
    font-weight: 500;
  }

  .primary {
    background: var(--accent);
    color: var(--accent-fg);
    border: 1px solid var(--accent);
    padding: 0.5rem 0.9rem;
    border-radius: 8px;
    cursor: pointer;
    font-weight: 600;
  }

  .empty {
    color: var(--fg-muted);
    text-align: center;
    padding: 1.5rem;
    background: var(--bg-2);
    border: 1px dashed var(--border);
    border-radius: var(--radius);
    margin: 0;
  }

  ul {
    list-style: none;
    margin: 0;
    padding: 0;
    display: flex;
    flex-direction: column;
    gap: 0.4rem;
  }
  li {
    display: grid;
    grid-template-columns: auto 1fr auto auto;
    gap: 0.6rem;
    align-items: center;
    padding: 0.55rem 0.7rem;
    background: var(--bg-2);
    border: 1px solid var(--border);
    border-radius: 8px;
  }

  .check {
    width: 22px;
    height: 22px;
    border-radius: 6px;
    border: 2px solid var(--accent);
    background: transparent;
    cursor: pointer;
    flex: none;
    padding: 0;
  }
  .check:hover {
    background: color-mix(in srgb, var(--accent) 20%, transparent);
  }

  .title {
    color: var(--fg);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .date-pick {
    display: flex;
    align-items: center;
    gap: 0.4rem;
    font-size: 0.75rem;
    color: var(--fg-muted);
  }
  .date-pick .lbl { text-transform: uppercase; letter-spacing: 0.05em; }
  .date-pick input {
    background: var(--bg-3);
    color: var(--fg);
    border: 1px solid var(--border);
    border-radius: 6px;
    padding: 0.3rem 0.4rem;
    font: inherit;
    font-size: 0.85rem;
  }

  .del {
    background: transparent;
    border: 1px solid var(--border);
    color: var(--fg-muted);
    width: 28px;
    height: 28px;
    border-radius: 6px;
    cursor: pointer;
    font-size: 1.1rem;
    line-height: 1;
  }
  .del:hover {
    background: var(--bg-3);
    color: var(--danger);
    border-color: var(--danger);
  }

  @media (max-width: 540px) {
    li { grid-template-columns: auto 1fr auto; }
    li .date-pick { grid-column: 1 / -1; justify-content: flex-start; }
  }
</style>
