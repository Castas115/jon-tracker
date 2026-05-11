<script lang="ts">
  import { isoWeekNumber, mondayOf, ymd } from './lib/dates';
  import type { Task } from './lib/types';

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

  function countThisWeek(t: Task): number {
    return t.completed_dates.filter((d) => {
      const date = new Date(d + 'T00:00:00');
      return isoWeekNumber(date) === thisWeekISO && date.getFullYear() === new Date().getFullYear();
    }).length;
  }

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
        {@const done = countThisWeek(t)}
        {@const target = t.target_per_week ?? 0}
        {@const todayDone = isDoneToday(t)}
        {@const hit = target > 0 && done >= target}
        <li class:hit>
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
          <span class="title">{t.title}</span>
          <span class="progress" aria-label={`${done} of ${target} this week`}>
            {#each Array(Math.max(target, done)) as _, i}
              <span class="dot" class:filled={i < done} class:over={i >= target}></span>
            {/each}
            <span class="ratio">{done}/{target}</span>
          </span>
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
    grid-template-columns: auto 1fr auto auto;
  }
  .goals li.hit {
    border-color: color-mix(in srgb, var(--today) 70%, var(--border));
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
