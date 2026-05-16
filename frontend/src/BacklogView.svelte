<script lang="ts">
  import { isoWeekNumber, ymd } from './lib/dates';
  import type { Task } from './lib/types';
  import { goalStatus, segmentLabel } from './lib/weeklyGoal';

  type Props = {
    tasks: Task[];
    onToggle: (task: Task, dateYMD: string) => void;
    onAssignDate: (task: Task, dateYMD: string) => void;
    onRemove: (task: Task) => void;
    onCreate: () => void;
    onCreateGoal: () => void;
  };

  const { tasks, onToggle, onAssignDate, onRemove, onCreate, onCreateGoal }: Props = $props();

  // Backlog = undated singles still pending (done singles graduate out).
  const singles = $derived(
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

  const todayYMD = ymd(new Date());
  const thisWeekISO = isoWeekNumber(new Date());

  const goals = $derived(
    tasks
      .filter((t) => t.task_type === 'weekly_goal')
      .sort((a, b) => a.title.localeCompare(b.title))
  );

  function handleDate(t: Task, value: string) {
    if (!value) return;
    onAssignDate(t, value);
  }

  function complete(t: Task) {
    onToggle(t, todayYMD);
  }

  function isDoneToday(t: Task): boolean {
    return t.completed_dates.includes(todayYMD);
  }
</script>

<section class="backlog">
  <header>
    <h2>Backlog <span class="count">{singles.length}</span></h2>
    <div class="actions">
      <button class="ghost" type="button" onclick={onCreateGoal}>+ Weekly goal</button>
      <button class="primary" type="button" onclick={onCreate}>+ Add</button>
    </div>
  </header>

  {#if goals.length > 0}
    <h3 class="section-label">Weekly goals · ISO W{thisWeekISO}</h3>
    <ul class="goals">
      {#each goals as t (t.id)}
        {@const status = goalStatus(t, todayYMD)}
        {@const todayDone = isDoneToday(t)}
        <li class:hit={status.hit} class:exceeded={status.exceeded}>
          <button
            class="goal-check"
            type="button"
            class:on={todayDone}
            aria-label={todayDone ? 'Undo today' : 'Mark today'}
            title={todayDone ? 'Undo today’s completion' : 'Add a completion for today'}
            onclick={() => complete(t)}
          >
            {#if todayDone}✓{:else}＋{/if}
          </button>
          <div class="goal-body">
            <div class="goal-head">
              <span class="title">{t.title}</span>
              {#if status.exceeded}
                <span class="badge over" title="Smashed it">⭐ +{status.done - status.target}</span>
              {:else if status.hit}
                <span class="badge hit" title="Goal hit">🎯 hit</span>
              {/if}
            </div>
            {#if status.segments.length === 0}
              <span class="progress" aria-label={`${status.done} of ${status.target} this week`}>
                {#each Array(Math.max(status.target, status.done)) as _, i}
                  <span class="dot" class:filled={i < status.done} class:over={i >= status.target}></span>
                {/each}
                <span class="ratio">{status.done}/{status.target}</span>
              </span>
            {:else}
              <div class="seg-progress">
                {#each status.segments as sp}
                  <div class="seg" class:hit={sp.hit} class:exceeded={sp.exceeded}>
                    <span class="seg-label">{segmentLabel(sp.segment)}</span>
                    <span class="progress">
                      {#each Array(Math.max(sp.segment.target, sp.done)) as _, i}
                        <span class="dot" class:filled={i < sp.done} class:over={i >= sp.segment.target}></span>
                      {/each}
                      <span class="ratio">{sp.done}/{sp.segment.target}</span>
                    </span>
                  </div>
                {/each}
              </div>
            {/if}
          </div>
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

  <h3 class="section-label">Single backlog · {singles.length}</h3>
  {#if singles.length === 0}
    <p class="empty">Nothing pending. Create undated singles to populate this list.</p>
  {:else}
    <ul>
      {#each singles as t (t.id)}
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
    width: 100%;
    margin: 0 auto;
    flex: 1;
    min-height: 0;
    overflow-y: auto;
    padding-right: 4px;
  }

  header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 0.75rem;
  }

  header .actions { display: flex; gap: 0.5rem; }
  .ghost {
    background: transparent;
    color: var(--fg-muted);
    border: 1px solid var(--border);
    padding: 0.5rem 0.9rem;
    border-radius: 8px;
    cursor: pointer;
  }
  .ghost:hover { color: var(--fg); background: var(--bg-3); }

  .section-label {
    margin: 0.5rem 0 0;
    font-size: 0.72rem;
    text-transform: uppercase;
    letter-spacing: 0.08em;
    color: var(--fg-muted);
    font-weight: 600;
  }

  .goals li {
    display: grid;
    grid-template-columns: auto 1fr auto;
    gap: 0.6rem;
    align-items: start;
    padding: 0.6rem 0.75rem;
    background: var(--bg-2);
    border: 1px solid var(--border);
    border-radius: 8px;
    list-style: none;
  }
  .goals li.hit {
    border-color: color-mix(in srgb, var(--today) 70%, var(--border));
    background: color-mix(in srgb, var(--today) 6%, var(--bg-2));
  }
  .goals li.exceeded {
    border-color: color-mix(in srgb, #ffb84d 70%, var(--border));
    background: color-mix(in srgb, #ffb84d 8%, var(--bg-2));
  }
  .goal-body {
    display: flex;
    flex-direction: column;
    gap: 0.35rem;
    min-width: 0;
  }
  .goal-head {
    display: flex;
    align-items: center;
    gap: 0.5rem;
    flex-wrap: wrap;
  }
  .badge {
    font-size: 0.72rem;
    padding: 1px 6px;
    border-radius: 999px;
    background: var(--bg-3);
    color: var(--fg);
    font-variant-numeric: tabular-nums;
  }
  .badge.hit {
    background: color-mix(in srgb, var(--today) 25%, var(--bg-3));
    color: var(--today);
  }
  .badge.over {
    background: color-mix(in srgb, #ffb84d 25%, var(--bg-3));
    color: #ffb84d;
  }
  .seg-progress {
    display: flex;
    flex-direction: column;
    gap: 0.25rem;
  }
  .seg {
    display: flex;
    align-items: center;
    gap: 0.5rem;
    padding: 2px 6px;
    border-radius: 6px;
    border: 1px solid transparent;
  }
  .seg.hit {
    border-color: color-mix(in srgb, var(--today) 40%, transparent);
  }
  .seg.exceeded {
    border-color: color-mix(in srgb, #ffb84d 50%, transparent);
  }
  .seg-label {
    font-size: 0.72rem;
    color: var(--fg-muted);
    text-transform: uppercase;
    letter-spacing: 0.05em;
    min-width: 4.2rem;
  }

  .goal-check {
    width: 28px;
    height: 28px;
    border-radius: 8px;
    border: 1px solid var(--accent);
    background: transparent;
    color: var(--accent);
    cursor: pointer;
    font-size: 1rem;
    line-height: 1;
    padding: 0;
  }
  .goal-check.on {
    background: var(--accent);
    color: var(--accent-fg);
  }
  .goal-check:hover { box-shadow: var(--shadow); }

  .progress {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    font-size: 0.8rem;
    color: var(--fg-muted);
    flex-wrap: nowrap;
  }
  .dot {
    width: 8px;
    height: 8px;
    border-radius: 50%;
    background: var(--bg-3);
    border: 1px solid var(--border);
  }
  .dot.filled {
    background: var(--accent);
    border-color: var(--accent);
  }
  .dot.over {
    background: var(--today);
    border-color: var(--today);
  }
  .ratio {
    margin-left: 4px;
    font-variant-numeric: tabular-nums;
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
