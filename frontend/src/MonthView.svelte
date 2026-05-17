<script lang="ts">
  import {
    MONTH_LABELS,
    addMonths,
    isToday,
    isoWeekNumber,
    monthGrid,
    weekdayMonFirst,
    ymd
  } from './lib/dates';
  import { type CalendarEvent } from './lib/api';
  import { WEEKDAY_LABELS, type Task } from './lib/types';
  import { weeklyGoalLabel } from './lib/weeklyGoal';
  import TodaySidebar from './TodaySidebar.svelte';

  type Props = {
    tasks: Task[];
    events?: CalendarEvent[];
    focusedDate?: string;
    onToggle: (task: Task, dateYMD: string) => void;
    onCreate: (dateYMD: string) => void;
  };

  let {
    tasks,
    events = [],
    focusedDate = $bindable(ymd(new Date())),
    onToggle,
    onCreate
  }: Props = $props();

  const now = new Date();
  let year = $state(now.getFullYear());
  let month = $state(now.getMonth());
  let selectedYMD = $state(ymd(now));

  // When the keyboard focus moves outside the visible month, follow it.
  // Keep the detail panel in sync with the focused date too.
  $effect(() => {
    const d = new Date(focusedDate + 'T00:00:00');
    if (d.getFullYear() !== year || d.getMonth() !== month) {
      year = d.getFullYear();
      month = d.getMonth();
    }
    selectedYMD = focusedDate;
  });

  const grid = $derived(monthGrid(year, month));
  const monthLabel = $derived(
    `${MONTH_LABELS[month]} ${year} · W${isoWeekNumber(new Date(focusedDate + 'T00:00:00'))}`
  );

  function tasksForDate(d: Date): Task[] {
    const wd = weekdayMonFirst(d);
    const k = ymd(d);
    return tasks
      .filter((t) => {
        if (t.task_type === 'recurring') return (t.weekdays ?? []).includes(wd);
        if (t.task_type === 'single') return t.fixed_date === k;
        if (t.task_type === 'birthday' && t.fixed_date) {
          const bd = new Date(t.fixed_date + 'T00:00:00');
          return bd.getMonth() === d.getMonth() && bd.getDate() === d.getDate();
        }
        if (t.task_type === 'weekly_goal') {
          if (t.show_in_upcoming === false) return false;
          const segs = t.target_segments ?? [];
          if (segs.length === 0) return true;
          return segs.some((s) => s.weekdays.includes(wd));
        }
        return false;
      })
      .sort((a, b) => {
        // Sort by start_time asc; tasks without a time fall to the bottom.
        const ta = a.start_time ?? '99:99';
        const tb = b.start_time ?? '99:99';
        return ta.localeCompare(tb) || a.title.localeCompare(b.title);
      });
  }

  function isCompleted(t: Task, dateYMD: string): boolean {
    return t.completed_dates.includes(dateYMD);
  }

  function eventDateYMD(ev: CalendarEvent): string {
    if (ev.all_day) return ev.start; // already YYYY-MM-DD
    return ymd(new Date(ev.start));
  }

  function eventsForDate(d: Date): CalendarEvent[] {
    const k = ymd(d);
    return events.filter((ev) => eventDateYMD(ev) === k);
  }

  function fmtTime(ev: CalendarEvent): string {
    if (ev.all_day) return '';
    const s = new Date(ev.start);
    const hh = String(s.getHours()).padStart(2, '0');
    const mm = String(s.getMinutes()).padStart(2, '0');
    return `${hh}:${mm}`;
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

</script>

<section class="month">
  <div class="nav">
    <button class="icon" type="button" aria-label="Previous month" onclick={() => nav(-1)}>‹</button>
    <strong>{monthLabel}</strong>
    <button class="icon" type="button" aria-label="Next month" onclick={() => nav(1)}>›</button>
    <button class="icon today-btn" type="button" onclick={goToday}>Today</button>
  </div>

  <div class="main">
  <div class="cal">
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
      {@const dayEvents = eventsForDate(d)}
      {@const todos = dayTasks.filter((t) => t.is_todo && t.task_type !== 'weekly_goal')}
      {@const doneCount = todos.filter((t) => isCompleted(t, k)).length}
      {@const total = todos.length}
      {@const items = [
        ...dayTasks.map((t) => ({
          kind: t.task_type === 'birthday' ? 'birthday-task' : 'task',
          title: t.task_type === 'weekly_goal' ? weeklyGoalLabel(t, k) : t.title,
          time: t.start_time ?? '',
          sortKey: t.start_time ?? '',
          done: t.is_todo && t.task_type !== 'weekly_goal' && isCompleted(t, k)
        })),
        ...dayEvents.map((e) => ({
          kind: e.kind === 'birthday' ? 'birthday-event' : 'event',
          title: e.title,
          time: fmtTime(e),
          sortKey: e.all_day ? '' : new Date(e.start).toISOString(),
          done: false
        }))
      ].sort((a, b) => a.sortKey.localeCompare(b.sortKey))}
      {@const visible = items.slice(0, 3)}
      {@const extra = items.length - visible.length}
      <button
        class="cell"
        class:out={!inMonth}
        class:today={isToday(d)}
        class:selected={selectedYMD === k}
        class:focused={focusedDate === k}
        class:has={items.length > 0}
        class:all-done={total > 0 && doneCount === total}
        type="button"
        onclick={() => {
          selectedYMD = k;
          onCreate(k);
        }}
        aria-label={`${d.getDate()} ${MONTH_LABELS[d.getMonth()]} - ${doneCount}/${total} tasks, ${dayEvents.length} events`}
      >
        <span class="num">{d.getDate()}</span>
        <span class="items">
          {#each visible as it}
            <span class="item kind-{it.kind}" class:done={it.done}>
              {#if it.kind === 'birthday-task' || it.kind === 'birthday-event'}🎂 {/if}{#if it.time}<span class="t">{it.time}</span> {/if}{it.title}
            </span>
          {/each}
          {#if extra > 0}
            <span class="more">+{extra} more</span>
          {/if}
        </span>
      </button>
    {/each}
  </div>
  </div>

  <TodaySidebar tasks={tasks} onToggle={onToggle} />
  </div>
</section>

<style>
  .month {
    display: flex;
    flex-direction: column;
    gap: 0.75rem;
    flex: 1;
    min-height: 0;
  }

  .main {
    display: grid;
    grid-template-columns: 1fr 320px;
    gap: 0.75rem;
    flex: 1;
    min-height: 0;
    min-width: 0;
    width: 100%;
  }
  .cal {
    display: flex;
    flex-direction: column;
    gap: 0.5rem;
    min-width: 0;
    min-height: 0;
    overflow-y: auto;
    padding-right: 4px;
  }

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
  }

  .grid {
    display: grid;
    grid-template-columns: repeat(7, 1fr);
    gap: 2px;
  }

  .cell {
    min-height: 110px;
    padding: 0.3rem 0.35rem;
    background: var(--bg-2);
    border: 1px solid var(--border);
    border-radius: 6px;
    color: inherit;
    display: flex;
    flex-direction: column;
    align-items: stretch;
    gap: 4px;
    cursor: pointer;
    font: inherit;
    text-align: left;
    overflow: hidden;
    transition: background-color 80ms ease, border-color 80ms ease;
  }
  .cell:hover { background: var(--bg-3); }
  .cell.out { opacity: 0.35; }
  .cell.has .num { font-weight: 600; }
  .cell.today {
    border-color: var(--today);
    color: var(--today);
  }
  .cell.selected {
    background: var(--accent);
    color: var(--accent-fg);
    border-color: var(--accent);
  }
  .cell.today.selected {
    background: var(--today);
    color: var(--today-fg);
    border-color: var(--today);
  }
  .cell.focused {
    box-shadow: 0 0 0 2px var(--accent);
  }
  .cell.today.focused {
    box-shadow: 0 0 0 2px var(--today);
  }
  .cell.selected .count { color: var(--accent-fg); }
  .cell.today.selected .count { color: var(--today-fg); }
  .cell.all-done:not(.selected) { background: color-mix(in srgb, var(--accent) 14%, var(--bg-2)); }

  .num {
    font-size: 0.85rem;
    text-align: left;
    line-height: 1;
  }

  .items {
    display: flex;
    flex-direction: column;
    gap: 2px;
    overflow: hidden;
  }
  .item {
    font-size: 0.7rem;
    line-height: 1.2;
    padding: 1px 4px;
    border-radius: 3px;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }
  .item.kind-task {
    background: color-mix(in srgb, var(--accent) 35%, transparent);
    color: var(--fg);
  }
  .item.kind-event {
    background: color-mix(in srgb, var(--gcal) 35%, transparent);
    color: var(--fg);
  }
  .item.kind-birthday-task {
    background: color-mix(in srgb, var(--accent) 30%, transparent);
    color: var(--fg);
  }
  .item.kind-birthday-event {
    background: color-mix(in srgb, #c97a8a 35%, transparent);
    color: var(--fg);
  }
  .item.done { opacity: 0.5; text-decoration: line-through; }
  .item .t {
    font-variant-numeric: tabular-nums;
    color: var(--fg-muted);
    font-weight: 500;
    margin-right: 5px;
  }
  .more {
    font-size: 0.65rem;
    color: var(--fg-muted);
    padding: 0 4px;
  }
  .cell.selected .item,
  .cell.selected .more { color: var(--accent-fg); }
  .cell.today.selected .item,
  .cell.today.selected .more { color: var(--today-fg); }

  @media (max-width: 960px) {
    .main { grid-template-columns: 1fr; }
    .cal { overflow-y: visible; }
  }

  @media (max-width: 480px) {
    .num { font-size: 0.8rem; }
    .count { font-size: 0.6rem; }
  }
</style>
