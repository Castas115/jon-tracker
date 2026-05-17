<script lang="ts">
  import { ymd } from './lib/dates';
  import { goalStatus, segmentLabel, streakInfo } from './lib/weeklyGoal';
  import type { Task } from './lib/types';

  type Props = {
    open: boolean;
    tasks: Task[];
    onClose: () => void;
  };

  const { open, tasks, onClose }: Props = $props();

  let dialog: HTMLDialogElement | null = $state(null);

  $effect(() => {
    if (!dialog) return;
    if (open && !dialog.open) dialog.showModal();
    else if (!open && dialog.open) dialog.close();
  });

  function handleBackdropClick(e: MouseEvent) {
    if (e.target === dialog) onClose();
  }

  const today = $derived(ymd(new Date()));

  type Row = {
    task: Task;
    current: number;
    best: number;
    weekDone: number;
    weekTarget: number;
    weekHit: boolean;
    segLabels: string;
  };

  const rows = $derived.by<Row[]>(() => {
    return tasks
      .filter((t) => t.task_type === 'weekly_goal')
      .map((t) => {
        const s = streakInfo(t, today);
        const g = goalStatus(t, today);
        const segs = t.target_segments ?? [];
        const segLabels =
          segs.length === 0
            ? `${t.target_per_week ?? 0}/wk`
            : segs.map((seg) => `${seg.target}x ${segmentLabel(seg)}`).join(' + ');
        return {
          task: t,
          current: s.current,
          best: s.best,
          weekDone: g.done,
          weekTarget: g.target,
          weekHit: g.hit,
          segLabels
        };
      })
      .sort((a, b) => b.current - a.current || b.best - a.best || a.task.title.localeCompare(b.task.title));
  });

  function flame(n: number): string {
    if (n === 0) return '·';
    if (n >= 12) return '🔥🔥🔥';
    if (n >= 6) return '🔥🔥';
    return '🔥';
  }
</script>

<dialog bind:this={dialog} onclose={onClose} onclick={handleBackdropClick}>
  <h2>Goal streaks</h2>
  {#if rows.length === 0}
    <p class="empty">No weekly goals yet.</p>
  {:else}
    <table>
      <thead>
        <tr>
          <th class="left">Goal</th>
          <th>This week</th>
          <th>Current</th>
          <th>Best</th>
        </tr>
      </thead>
      <tbody>
        {#each rows as r}
          <tr>
            <td class="left">
              <span class="title">{r.task.title}</span>
              <span class="sub">{r.segLabels}</span>
            </td>
            <td>
              <span class="week" class:hit={r.weekHit}>{r.weekDone}/{r.weekTarget}</span>
            </td>
            <td>
              <span class="flame">{flame(r.current)}</span>
              <span class="num">{r.current}</span>
            </td>
            <td>
              <span class="num">{r.best}</span>
            </td>
          </tr>
        {/each}
      </tbody>
    </table>
  {/if}
  <div class="actions">
    <button type="button" class="primary" onclick={onClose}>Close</button>
  </div>
</dialog>

<style>
  dialog {
    background: var(--bg-2);
    color: var(--fg);
    border: 1px solid var(--border);
    border-radius: var(--radius);
    padding: 1.25rem;
    width: min(520px, 94vw);
    max-height: 88vh;
    overflow-y: auto;
    box-shadow: 0 20px 60px rgba(0, 0, 0, 0.5);
  }
  dialog::backdrop {
    background: rgba(0, 0, 0, 0.6);
  }
  h2 {
    margin: 0 0 0.85rem;
    font-size: 1.05rem;
    font-weight: 600;
  }
  .empty {
    color: var(--fg-muted);
    font-size: 0.9rem;
  }
  table {
    width: 100%;
    border-collapse: collapse;
    font-size: 0.88rem;
  }
  th {
    font-size: 0.7rem;
    font-weight: 600;
    text-transform: uppercase;
    letter-spacing: 0.06em;
    color: var(--fg-muted);
    padding: 0.4rem 0.4rem;
    text-align: center;
    border-bottom: 1px solid var(--border);
  }
  th.left { text-align: left; }
  td {
    padding: 0.5rem 0.4rem;
    border-bottom: 1px solid var(--border);
    text-align: center;
    vertical-align: middle;
  }
  td.left { text-align: left; }
  tr:last-child td { border-bottom: none; }
  .title {
    display: block;
    font-weight: 500;
  }
  .sub {
    display: block;
    font-size: 0.72rem;
    color: var(--fg-muted);
  }
  .week {
    font-variant-numeric: tabular-nums;
    color: var(--fg-muted);
  }
  .week.hit {
    color: var(--accent);
    font-weight: 600;
  }
  .flame {
    margin-right: 4px;
  }
  .num {
    font-variant-numeric: tabular-nums;
    font-weight: 600;
  }
  .actions {
    display: flex;
    justify-content: flex-end;
    margin-top: 0.85rem;
  }
  .actions .primary {
    background: var(--accent);
    color: var(--accent-fg);
    border: 1px solid var(--accent);
    padding: 0.5rem 1rem;
    border-radius: 8px;
    cursor: pointer;
    font-weight: 600;
  }
</style>
