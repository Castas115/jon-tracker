<script lang="ts">
  import {
    MONTH_LABELS,
    addMonths,
    isToday,
    monthGrid,
    weekdayMonFirst,
    ymd
  } from './lib/dates';
  import { type CalendarEvent } from './lib/api';
  import { WEEKDAY_LABELS, type Task } from './lib/types';

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
  $effect(() => {
    const d = new Date(focusedDate + 'T00:00:00');
    if (d.getFullYear() !== year || d.getMonth() !== month) {
      year = d.getFullYear();
      month = d.getMonth();
    }
  });

  const grid = $derived(monthGrid(year, month));
  const monthLabel = $derived(`${MONTH_LABELS[month]} ${year}`);

  function tasksForDate(d: Date): Task[] {
    const wd = weekdayMonFirst(d);
    const k = ymd(d);
    return tasks.filter((t) => {
      if (t.task_type === 'recurring') return (t.weekdays ?? []).includes(wd);
      if (t.task_type === 'fixed') return t.fixed_date === k;
      if (t.task_type === 'birthday' && t.fixed_date) {
        const bd = new Date(t.fixed_date + 'T00:00:00');
        return bd.getMonth() === d.getMonth() && bd.getDate() === d.getDate();
      }
      return false;
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

  const selectedDate = $derived(new Date(selectedYMD + 'T00:00:00'));
  const selectedTasks = $derived(tasksForDate(selectedDate));
  const selectedEvents = $derived(eventsForDate(selectedDate));
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
      {@const dayEvents = eventsForDate(d)}
      {@const doneCount = dayTasks.filter((t) => isCompleted(t, k)).length}
      {@const total = dayTasks.length}
      {@const items = [
        ...dayTasks.map((t) => ({
          kind: t.task_type === 'birthday' ? 'birthday-task' : 'task',
          title: t.title,
          time: t.start_time ?? '',
          sortKey: t.start_time ?? '',
          done: isCompleted(t, k)
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

  <article class="day-detail">
    <h3>
      {selectedDate.getDate()} {MONTH_LABELS[selectedDate.getMonth()]}
      {#if isToday(selectedDate)}<em> · today</em>{/if}
    </h3>
    {#if selectedTasks.length === 0 && selectedEvents.length === 0}
      <p class="empty">Nothing this day</p>
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
              <span>
                {t.task_type === 'birthday' ? '🎂 ' : ''}{t.title}
              </span>
            </label>
          </li>
        {/each}
        {#each selectedEvents as ev (ev.id)}
          <li class="event-row" class:birthday={ev.kind === 'birthday'}>
            <span class="ev-time">{fmtTime(ev) || 'all day'}</span>
            <span class="ev-title">
              {ev.kind === 'birthday' ? '🎂 ' : ''}{ev.title}
            </span>
            <span class="ev-tag" aria-label="Google">G</span>
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
    border-color: var(--accent);
    color: var(--accent);
  }
  .cell.selected {
    background: var(--accent);
    color: var(--accent-fg);
    border-color: var(--accent);
  }
  .cell.focused {
    box-shadow: 0 0 0 2px var(--accent);
  }
  .cell.selected .count { color: var(--accent-fg); }
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

  .event-row {
    color: var(--gcal);
    gap: 0.5rem;
  }
  .event-row.birthday { color: #c97a8a; }
  .ev-time {
    font-variant-numeric: tabular-nums;
    font-size: 0.78rem;
    color: var(--fg-muted);
    flex: none;
    width: 4rem;
  }
  .ev-title { flex: 1; }
  .ev-tag {
    font-size: 0.6rem;
    font-weight: 700;
    padding: 1px 5px;
    border-radius: 3px;
    background: color-mix(in srgb, var(--gcal) 30%, transparent);
    color: var(--gcal);
    letter-spacing: 0.04em;
  }
  .event-row.birthday .ev-tag {
    background: color-mix(in srgb, #c97a8a 30%, transparent);
    color: #c97a8a;
  }

  @media (max-width: 480px) {
    .num { font-size: 0.8rem; }
    .count { font-size: 0.6rem; }
  }
</style>
