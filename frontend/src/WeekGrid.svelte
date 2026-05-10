<script lang="ts">
  import { weekDates, todayWeekday } from './lib/api';
  import { WEEKDAY_LABELS, type Task } from './lib/types';

  type Props = {
    tasks: Task[];
    onToggle: (task: Task, dateYMD: string) => void;
    onRemove: (task: Task) => void;
  };

  const { tasks, onToggle, onRemove }: Props = $props();

  const HOUR_START = 6;
  const HOUR_END = 24; // exclusive (so last cell is 23:00)
  const HOURS = Array.from({ length: HOUR_END - HOUR_START }, (_, i) => HOUR_START + i);
  const CELL_PX = 48; // height per hour cell

  const dates = weekDates();
  const today = todayWeekday();

  function toMinutes(t: string | null): number | null {
    if (!t) return null;
    const [h, m] = t.split(':').map(Number);
    return h * 60 + m;
  }

  function blockStyle(task: Task): string {
    const start = toMinutes(task.start_time);
    const end = toMinutes(task.end_time) ?? (start !== null ? start + 60 : null);
    if (start === null || end === null) return '';
    const top = ((start - HOUR_START * 60) / 60) * CELL_PX;
    const height = ((end - start) / 60) * CELL_PX;
    return `top: ${Math.max(0, top)}px; height: ${Math.max(20, height - 2)}px;`;
  }

  function timedTasksForDay(weekday: number): Task[] {
    return tasks
      .filter((t) => t.weekday === weekday && t.start_time !== null)
      .sort((a, b) => (a.start_time ?? '').localeCompare(b.start_time ?? ''));
  }

  function allDayTasksForDay(weekday: number): Task[] {
    return tasks.filter((t) => t.weekday === weekday && t.start_time === null);
  }

  function isDone(t: Task, dateYMD: string): boolean {
    return t.completed_dates.includes(dateYMD);
  }

  function fmtTime(t: Task): string {
    if (!t.start_time) return '';
    return t.end_time ? `${t.start_time}–${t.end_time}` : t.start_time;
  }

  function dayDateLabel(weekday: number): string {
    const d = new Date(dates[weekday] + 'T00:00:00');
    return String(d.getDate());
  }

  const gridHeight = (HOUR_END - HOUR_START) * CELL_PX;
</script>

<section class="grid-wrap">
  <div class="header-row">
    <span class="time-col"></span>
    {#each WEEKDAY_LABELS as label, i}
      <div class="day-head" class:today={i === today}>
        <span class="d-label">{label}</span>
        <span class="d-num">{dayDateLabel(i)}</span>
      </div>
    {/each}
  </div>

  <div class="all-day">
    <span class="time-col tag">Todo el día</span>
    {#each WEEKDAY_LABELS as _label, weekday}
      {@const items = allDayTasksForDay(weekday)}
      <div class="all-day-cell">
        {#each items as t (t.id)}
          {@const dateYMD = dates[weekday]}
          {@const done = isDone(t, dateYMD)}
          <button
            class="all-day-block"
            class:done
            type="button"
            onclick={() => onToggle(t, dateYMD)}
            oncontextmenu={(e) => {
              e.preventDefault();
              onRemove(t);
            }}
            title={`${t.title} (clic: marcar · clic-derecho: borrar)`}
          >
            {t.title}
          </button>
        {/each}
      </div>
    {/each}
  </div>

  <div class="body" style:--cell={`${CELL_PX}px`} style:height={`${gridHeight}px`}>
    <div class="hours">
      {#each HOURS as h}
        <span class="hour-label">{String(h).padStart(2, '0')}:00</span>
      {/each}
    </div>

    {#each WEEKDAY_LABELS as _label, weekday}
      <div class="day-col" class:today={weekday === today}>
        {#each HOURS as _h}
          <div class="hour-cell"></div>
        {/each}

        {#each timedTasksForDay(weekday) as t (t.id)}
          {@const dateYMD = dates[weekday]}
          {@const done = isDone(t, dateYMD)}
          <button
            class="block"
            class:done
            type="button"
            style={blockStyle(t)}
            onclick={() => onToggle(t, dateYMD)}
            oncontextmenu={(e) => {
              e.preventDefault();
              onRemove(t);
            }}
            title={`${t.title} ${fmtTime(t)} (clic: marcar · clic-derecho: borrar)`}
          >
            <span class="b-title">{t.title}</span>
            <span class="b-time">{fmtTime(t)}</span>
          </button>
        {/each}
      </div>
    {/each}
  </div>
</section>

<style>
  .grid-wrap {
    border: 1px solid var(--border);
    border-radius: var(--radius);
    background: var(--bg-2);
    overflow: hidden;
  }

  .header-row, .all-day {
    display: grid;
    grid-template-columns: 56px repeat(7, 1fr);
    border-bottom: 1px solid var(--border);
  }

  .day-head {
    text-align: center;
    padding: 0.4rem 0.25rem;
    border-left: 1px solid var(--border);
    font-size: 0.7rem;
    color: var(--fg-muted);
    text-transform: uppercase;
    letter-spacing: 0.06em;
    line-height: 1.2;
    display: flex;
    flex-direction: column;
    gap: 2px;
  }
  .day-head .d-num {
    font-size: 1rem;
    color: var(--fg);
    text-transform: none;
    font-weight: 600;
  }
  .day-head.today { color: var(--accent); }
  .day-head.today .d-num { color: var(--accent); }

  .time-col {
    display: block;
  }
  .tag {
    font-size: 0.65rem;
    color: var(--fg-muted);
    padding: 0.4rem 0.3rem;
    text-align: right;
    line-height: 1.1;
  }

  .all-day-cell {
    border-left: 1px solid var(--border);
    padding: 4px;
    min-height: 28px;
    display: flex;
    flex-direction: column;
    gap: 2px;
  }
  .all-day-block {
    background: color-mix(in srgb, var(--accent) 75%, transparent);
    color: var(--accent-fg);
    border: none;
    border-radius: 4px;
    padding: 2px 6px;
    font-size: 0.72rem;
    font: inherit;
    cursor: pointer;
    text-align: left;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .all-day-block.done { opacity: 0.45; text-decoration: line-through; }

  .body {
    display: grid;
    grid-template-columns: 56px repeat(7, 1fr);
    position: relative;
  }

  .hours {
    display: grid;
    grid-template-rows: repeat(var(--rows, 18), var(--cell));
    border-right: 1px solid var(--border);
  }
  .hour-label {
    font-size: 0.65rem;
    color: var(--fg-muted);
    padding: 2px 4px 0 0;
    text-align: right;
    border-top: 1px solid var(--border);
    height: var(--cell);
    box-sizing: border-box;
  }
  .hour-label:first-child { border-top: none; }

  .day-col {
    position: relative;
    border-left: 1px solid var(--border);
  }
  .day-col.today { background: color-mix(in srgb, var(--accent) 6%, transparent); }

  .hour-cell {
    height: var(--cell);
    border-top: 1px solid var(--border);
  }
  .day-col .hour-cell:first-child { border-top: none; }

  .block {
    position: absolute;
    left: 2px;
    right: 2px;
    background: var(--accent);
    color: var(--accent-fg);
    border: none;
    border-radius: 6px;
    padding: 4px 6px;
    cursor: pointer;
    text-align: left;
    overflow: hidden;
    font: inherit;
    display: flex;
    flex-direction: column;
    gap: 2px;
  }
  .block.done {
    opacity: 0.45;
    text-decoration: line-through;
  }
  .b-title {
    font-size: 0.75rem;
    font-weight: 600;
    line-height: 1.1;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }
  .b-time {
    font-size: 0.65rem;
    opacity: 0.85;
    line-height: 1;
  }

  @media (max-width: 600px) {
    .header-row, .all-day, .body { grid-template-columns: 44px repeat(7, minmax(46px, 1fr)); }
    .day-head .d-label { font-size: 0.6rem; }
    .day-head .d-num { font-size: 0.85rem; }
    .b-title { font-size: 0.65rem; }
    .b-time { display: none; }
    .grid-wrap { overflow-x: auto; }
  }
</style>
