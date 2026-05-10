<script lang="ts">
  import {
    MONTH_LABELS,
    addMonths,
    isToday,
    monthGrid,
    weekdayMonFirst,
    ymd
  } from './lib/dates';
  import { WEEKDAY_LABELS, type Task } from './lib/types';

  type Props = {
    tasks: Task[];
    onToggle: (task: Task, dateYMD: string) => void;
  };

  const { tasks, onToggle }: Props = $props();

  const now = new Date();
  let year = $state(now.getFullYear());
  let month = $state(now.getMonth());
  let selectedYMD = $state(ymd(now));

  const grid = $derived(monthGrid(year, month));
  const monthLabel = $derived(`${MONTH_LABELS[month]} ${year}`);

  function tasksForDate(d: Date): Task[] {
    const wd = weekdayMonFirst(d);
    const k = ymd(d);
    return tasks.filter((t) => {
      if (t.task_type === 'recurring') return (t.weekdays ?? []).includes(wd);
      return t.fixed_date === k;
    });
  }

  function isCompleted(t: Task, dateYMD: string): boolean {
    return t.completed_dates.includes(dateYMD);
  }

  function nav(delta: number) {
    const next = addMonths(year, month, delta);
    year = next.year;
    month = next.month;
  }

  function goToday() {
    const t = new Date();
    year = t.getFullYear();
    month = t.getMonth();
    selectedYMD = ymd(t);
  }

  const selectedDate = $derived(new Date(selectedYMD + 'T00:00:00'));
  const selectedTasks = $derived(tasksForDate(selectedDate));
</script>

<section class="month">
  <div class="nav">
    <button class="icon" type="button" aria-label="Previous month" onclick={() => nav(-1)}>‹</button>
    <strong>{monthLabel}</strong>
    <button class="icon" type="button" aria-label="Next month" onclick={() => nav(1)}>›</button>
    <button class="icon today-btn" type="button" onclick={goToday}>Today</button>
  </div>

  <div class="dow">
    {#each WEEKDAY_LABELS as label}
      <span>{label}</span>
    {/each}
  </div>

  <div class="grid">
    {#each grid as d}
      {@const k = ymd(d)}
      {@const inMonth = d.getMonth() === month}
      {@const dayTasks = tasksForDate(d)}
      {@const doneCount = dayTasks.filter((t) => isCompleted(t, k)).length}
      {@const total = dayTasks.length}
      <button
        class="cell"
        class:out={!inMonth}
        class:today={isToday(d)}
        class:selected={selectedYMD === k}
        class:has={total > 0}
        class:all-done={total > 0 && doneCount === total}
        type="button"
        onclick={() => (selectedYMD = k)}
        aria-label={`${d.getDate()} ${MONTH_LABELS[d.getMonth()]} - ${doneCount}/${total} tasks`}
      >
        <span class="num">{d.getDate()}</span>
        {#if total > 0}
          <span class="count">{doneCount}/{total}</span>
        {/if}
      </button>
    {/each}
  </div>

  <article class="day-detail">
    <h3>
      {selectedDate.getDate()} {MONTH_LABELS[selectedDate.getMonth()]}
      {#if isToday(selectedDate)}<em> · today</em>{/if}
    </h3>
    {#if selectedTasks.length === 0}
      <p class="empty">No tasks this day</p>
    {:else}
      <ul>
        {#each selectedTasks as t (t.id)}
          {@const done = isCompleted(t, selectedYMD)}
          <li class:done>
            <label>
              <input
                type="checkbox"
                checked={done}
                onchange={() => onToggle(t, selectedYMD)}
              />
              <span>{t.title}</span>
            </label>
          </li>
        {/each}
      </ul>
    {/if}
  </article>
</section>

<style>
  .month { display: flex; flex-direction: column; gap: 0.75rem; }

  .nav {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 0.5rem;
  }
  .nav strong {
    flex: 1;
    text-align: center;
    font-size: 1rem;
    text-transform: capitalize;
  }
  .today-btn {
    font-size: 0.75rem;
    padding: 0.3rem 0.6rem;
    border: 1px solid var(--border);
  }

  .dow {
    display: grid;
    grid-template-columns: repeat(7, 1fr);
    gap: 2px;
    font-size: 0.7rem;
    text-transform: uppercase;
    letter-spacing: 0.06em;
    color: var(--fg-muted);
    text-align: center;
    padding: 0 2px;
  }

  .grid {
    display: grid;
    grid-template-columns: repeat(7, 1fr);
    gap: 2px;
  }

  .cell {
    aspect-ratio: 1 / 1;
    padding: 0.25rem;
    background: var(--bg-2);
    border: 1px solid var(--border);
    border-radius: 6px;
    color: inherit;
    display: flex;
    flex-direction: column;
    justify-content: space-between;
    align-items: stretch;
    cursor: pointer;
    font: inherit;
    transition: background-color 80ms ease, border-color 80ms ease;
  }
  .cell:hover { background: var(--bg-3); }
  .cell.out { opacity: 0.35; }
  .cell.has .num { font-weight: 600; }
  .cell.today {
    border-color: var(--accent);
    color: var(--accent);
  }
  .cell.selected {
    background: var(--accent);
    color: var(--accent-fg);
    border-color: var(--accent);
  }
  .cell.selected .count { color: var(--accent-fg); }
  .cell.all-done:not(.selected) { background: color-mix(in srgb, var(--accent) 14%, var(--bg-2)); }

  .num {
    font-size: 0.85rem;
    text-align: left;
    line-height: 1;
  }

  .count {
    font-size: 0.65rem;
    text-align: right;
    color: var(--fg-muted);
    letter-spacing: 0.04em;
  }

  .day-detail {
    margin-top: 0.5rem;
  }
  .day-detail h3 {
    margin: 0 0 0.5rem;
    font-size: 0.95rem;
    font-weight: 600;
    text-transform: capitalize;
  }
  .day-detail h3 em {
    color: var(--accent);
    font-style: normal;
    font-weight: 400;
  }
  .day-detail .empty {
    color: var(--fg-muted);
    font-size: 0.9rem;
    margin: 0.5rem 0;
  }
  .day-detail ul {
    list-style: none;
    margin: 0;
    padding: 0;
    display: flex;
    flex-direction: column;
    gap: 0.4rem;
  }
  .day-detail li {
    display: flex;
    align-items: center;
    gap: 0.5rem;
    padding: 0.45rem 0;
    border-bottom: 1px solid var(--border);
  }
  .day-detail li:last-child { border-bottom: none; }
  .day-detail li label {
    flex: 1;
    display: flex;
    align-items: center;
    gap: 0.6rem;
    cursor: pointer;
    user-select: none;
  }
  .day-detail li input[type='checkbox'] {
    width: 22px;
    height: 22px;
    accent-color: var(--accent);
    flex: none;
  }
  .day-detail li.done span {
    color: var(--done);
    text-decoration: line-through;
  }

  @media (max-width: 480px) {
    .num { font-size: 0.8rem; }
    .count { font-size: 0.6rem; }
  }
</style>
