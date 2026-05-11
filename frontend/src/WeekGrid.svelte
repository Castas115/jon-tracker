<script lang="ts">
  import { type CalendarEvent } from './lib/api';
  import { MONTH_LABELS, isoWeekNumber, weekDatesFromMonday, ymd } from './lib/dates';
  import { WEEKDAY_LABELS, type Task } from './lib/types';
  import { weeklyGoalLabel } from './lib/weeklyGoal';

  type Props = {
    tasks: Task[];
    events?: CalendarEvent[];
    weekStartYMD: string;
    focusedWeekday?: number;
    focusedHour?: number;
    onToggle: (task: Task, dateYMD: string) => void;
    onRemove: (task: Task) => void;
    onCreate: (weekday: number, dateYMD: string, start: string, end: string) => void;
  };

  const {
    tasks,
    events = [],
    weekStartYMD,
    focusedWeekday = -1,
    focusedHour = -1,
    onToggle,
    onRemove,
    onCreate
  }: Props = $props();

  const HOUR_START = 6;
  const HOUR_END = 24; // exclusive (so last cell is 23:00)
  const HOURS = Array.from({ length: HOUR_END - HOUR_START }, (_, i) => HOUR_START + i);
  const CELL_PX = 48; // height per hour cell
  // Working hours window highlighted on Mon..Fri (08:30 → 17:00).
  const WORK_START_MIN = 8 * 60 + 30;
  const WORK_END_MIN = 17 * 60;
  const WORK_TOP_PX = ((WORK_START_MIN - HOUR_START * 60) / 60) * CELL_PX;
  const WORK_HEIGHT_PX = ((WORK_END_MIN - WORK_START_MIN) / 60) * CELL_PX;

  const dates = $derived(weekDatesFromMonday(new Date(weekStartYMD + 'T00:00:00')));
  const todayYMD = ymd(new Date());
  const today = $derived(dates.indexOf(todayYMD));
  const weekLabel = $derived.by(() => {
    const mon = new Date(dates[0] + 'T00:00:00');
    const sun = new Date(dates[6] + 'T00:00:00');
    const w = isoWeekNumber(mon);
    const sameMonth = mon.getMonth() === sun.getMonth();
    const sameYear = mon.getFullYear() === sun.getFullYear();
    const monthMon = MONTH_LABELS[mon.getMonth()].slice(0, 3);
    const monthSun = MONTH_LABELS[sun.getMonth()].slice(0, 3);
    if (sameMonth && sameYear) {
      return `W${w} · ${mon.getDate()}–${sun.getDate()} ${monthMon} ${sun.getFullYear()}`;
    }
    if (sameYear) {
      return `W${w} · ${mon.getDate()} ${monthMon} – ${sun.getDate()} ${monthSun} ${sun.getFullYear()}`;
    }
    return `W${w} · ${mon.getDate()} ${monthMon} ${mon.getFullYear()} – ${sun.getDate()} ${monthSun} ${sun.getFullYear()}`;
  });

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

  function matchesDay(t: Task, weekday: number, dateYMD: string): boolean {
    if (t.task_type === 'recurring') {
      return (t.weekdays ?? []).includes(weekday);
    }
    if (t.task_type === 'single') {
      return t.fixed_date === dateYMD;
    }
    if (t.task_type === 'birthday' && t.fixed_date) {
      const d = new Date(dateYMD + 'T00:00:00');
      const bd = new Date(t.fixed_date + 'T00:00:00');
      return bd.getMonth() === d.getMonth() && bd.getDate() === d.getDate();
    }
    if (t.task_type === 'weekly_goal') {
      return t.completed_dates.includes(dateYMD);
    }
    return false;
  }

  function displayTitle(t: Task, dateYMD: string): string {
    if (t.task_type === 'weekly_goal') return weeklyGoalLabel(t, dateYMD);
    return t.title;
  }

  function timedTasksForDay(weekday: number): Task[] {
    const dateYMD = dates[weekday];
    return tasks
      .filter((t) => matchesDay(t, weekday, dateYMD) && t.start_time !== null)
      .sort((a, b) => (a.start_time ?? '').localeCompare(b.start_time ?? ''));
  }

  function allDayTasksForDay(weekday: number): Task[] {
    const dateYMD = dates[weekday];
    return tasks
      .filter((t) => matchesDay(t, weekday, dateYMD) && t.start_time === null)
      .sort((a, b) => a.title.localeCompare(b.title));
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

  type ParsedEvent = {
    id: string;
    title: string;
    kind: 'event' | 'birthday';
    weekday: number;
    all_day: boolean;
    start_min: number | null;
    end_min: number | null;
  };

  function parseEventsForWeek(): ParsedEvent[] {
    const out: ParsedEvent[] = [];
    const monday = new Date(dates[0] + 'T00:00:00');
    const sunday = new Date(dates[6] + 'T23:59:59');
    for (const ev of events) {
      let startDate: Date;
      let endDate: Date | null = null;
      let allDay = ev.all_day;
      if (allDay) {
        // Date strings YYYY-MM-DD
        startDate = new Date(ev.start + 'T00:00:00');
        endDate = ev.end ? new Date(ev.end + 'T00:00:00') : null;
      } else {
        startDate = new Date(ev.start);
        endDate = ev.end ? new Date(ev.end) : null;
      }
      if (startDate < monday || startDate > sunday) continue;

      const wd = startDate.getDay() === 0 ? 6 : startDate.getDay() - 1;
      if (allDay) {
        out.push({
          id: ev.id,
          title: ev.title,
          kind: ev.kind,
          weekday: wd,
          all_day: true,
          start_min: null,
          end_min: null
        });
      } else {
        const startMin = startDate.getHours() * 60 + startDate.getMinutes();
        const endMin = endDate
          ? endDate.getHours() * 60 + endDate.getMinutes()
          : startMin + 60;
        out.push({
          id: ev.id,
          title: ev.title,
          kind: ev.kind,
          weekday: wd,
          all_day: false,
          start_min: startMin,
          end_min: endMin
        });
      }
    }
    return out;
  }

  const parsedEvents = $derived(parseEventsForWeek());

  function eventBlockStyle(ev: ParsedEvent): string {
    if (ev.start_min === null || ev.end_min === null) return '';
    const top = ((ev.start_min - HOUR_START * 60) / 60) * CELL_PX;
    const height = ((ev.end_min - ev.start_min) / 60) * CELL_PX;
    return `top: ${Math.max(0, top)}px; height: ${Math.max(20, height - 2)}px;`;
  }

  function timedEventsForDay(weekday: number): ParsedEvent[] {
    return parsedEvents
      .filter((e) => e.weekday === weekday && !e.all_day)
      .sort((a, b) => (a.start_min ?? 0) - (b.start_min ?? 0));
  }

  function allDayEventsForDay(weekday: number): ParsedEvent[] {
    return parsedEvents.filter((e) => e.weekday === weekday && e.all_day);
  }

  const gridHeight = (HOUR_END - HOUR_START) * CELL_PX;

  function handleColClick(e: MouseEvent, weekday: number) {
    const target = e.target as HTMLElement;
    if (target.closest('.block')) return; // clicking a task → its handler runs
    const rect = (e.currentTarget as HTMLElement).getBoundingClientRect();
    const y = e.clientY - rect.top;
    let hour = Math.floor(y / CELL_PX) + HOUR_START;
    if (hour < HOUR_START) hour = HOUR_START;
    if (hour >= HOUR_END - 1) hour = HOUR_END - 2;
    const pad = (n: number) => String(n).padStart(2, '0');
    onCreate(weekday, dates[weekday], `${pad(hour)}:00`, `${pad(hour + 1)}:00`);
  }
</script>

<section class="grid-wrap">
  <div class="week-label"><strong>{weekLabel}</strong></div>
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
    <span class="time-col tag">All day</span>
    {#each WEEKDAY_LABELS as _label, weekday}
      {@const items = allDayTasksForDay(weekday)}
      {@const evs = allDayEventsForDay(weekday)}
      <div class="all-day-cell">
        {#each items as t (t.id)}
          {@const dateYMD = dates[weekday]}
          {@const done = t.is_todo && t.task_type !== 'weekly_goal' && isDone(t, dateYMD)}
          <button
            class="all-day-block"
            class:done
            class:info={!t.is_todo}
            class:bday-task={t.task_type === 'birthday'}
            type="button"
            onclick={() => t.is_todo && onToggle(t, dateYMD)}
            oncontextmenu={(e) => {
              e.preventDefault();
              onRemove(t);
            }}
            title={`${t.task_type === 'birthday' ? '🎂 ' : ''}${t.title}${t.is_todo ? ' (click: toggle · right-click: delete)' : ' (right-click: delete)'}`}
          >
            {t.task_type === 'birthday' ? '🎂 ' : ''}{displayTitle(t, dateYMD)}
          </button>
        {/each}
        {#each evs as ev (ev.id)}
          <div
            class="all-day-block event"
            class:birthday={ev.kind === 'birthday'}
            title={`${ev.kind === 'birthday' ? '🎂 ' : ''}${ev.title} (Google Calendar)`}
          >
            <span class="ev-text">
              {ev.kind === 'birthday' ? '🎂 ' : ''}{ev.title}
            </span>
            <span class="ev-badge" aria-label="Google">G</span>
          </div>
        {/each}
      </div>
    {/each}
  </div>

  <div class="body-scroll">
  <div class="body" style:--cell={`${CELL_PX}px`} style:height={`${gridHeight}px`}>
    <div class="hours">
      {#each HOURS as h}
        <span class="hour-label">{String(h).padStart(2, '0')}:00</span>
      {/each}
    </div>

    {#each WEEKDAY_LABELS as _label, weekday}
      <div
        class="day-col"
        class:today={weekday === today}
        role="button"
        tabindex="0"
        aria-label={`Add task for ${WEEKDAY_LABELS[weekday]}`}
        onclick={(e) => handleColClick(e, weekday)}
        onkeydown={(e) => {
          if (e.key === 'Enter' || e.key === ' ') {
            e.preventDefault();
            handleColClick(e as unknown as MouseEvent, weekday);
          }
        }}
      >
        {#if weekday < 5}
          <div
            class="work-band"
            style:top="{WORK_TOP_PX}px"
            style:height="{WORK_HEIGHT_PX}px"
          ></div>
        {/if}
        {#each HOURS as h}
          <div
            class="hour-cell"
            class:focused={weekday === focusedWeekday && h === focusedHour}
          ></div>
        {/each}

        {#each timedTasksForDay(weekday) as t (t.id)}
          {@const dateYMD = dates[weekday]}
          {@const done = t.is_todo && t.task_type !== 'weekly_goal' && isDone(t, dateYMD)}
          <button
            class="block"
            class:done
            class:info={!t.is_todo}
            type="button"
            style={blockStyle(t)}
            onclick={() => t.is_todo && onToggle(t, dateYMD)}
            oncontextmenu={(e) => {
              e.preventDefault();
              onRemove(t);
            }}
            title={`${t.title} ${fmtTime(t)}${t.is_todo ? ' (click: toggle · right-click: delete)' : ' (right-click: delete)'}`}
          >
            <span class="b-title">{t.title}</span>
            <span class="b-time">{fmtTime(t)}</span>
          </button>
        {/each}

        {#each timedEventsForDay(weekday) as ev (ev.id)}
          <div
            class="block event"
            class:birthday={ev.kind === 'birthday'}
            style={eventBlockStyle(ev)}
            title={`${ev.kind === 'birthday' ? '🎂 ' : ''}${ev.title} (Google Calendar)`}
          >
            <span class="b-title">
              {ev.kind === 'birthday' ? '🎂 ' : ''}{ev.title}
            </span>
            <span class="ev-badge" aria-label="Google">G</span>
          </div>
        {/each}
      </div>
    {/each}
  </div>
  </div>
</section>

<style>
  .grid-wrap {
    border: 1px solid var(--border);
    border-radius: var(--radius);
    background: var(--bg-2);
    overflow: hidden;
    display: flex;
    flex-direction: column;
    flex: 1;
    min-height: 0;
  }
  .body-scroll {
    flex: 1;
    min-height: 0;
    overflow-y: auto;
  }

  .week-label {
    padding: 0.5rem 0.75rem;
    border-bottom: 1px solid var(--border);
    text-align: center;
    font-size: 0.9rem;
    color: var(--fg);
    background: var(--bg-2);
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
  .day-head.today { color: var(--today); }
  .day-head.today .d-num { color: var(--today); }

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
  .day-col.today { background: color-mix(in srgb, var(--today) 8%, transparent); }

  .work-band {
    position: absolute;
    left: 0;
    right: 0;
    background: color-mix(in srgb, var(--accent) 7%, transparent);
    border-top: 1px dashed color-mix(in srgb, var(--accent) 30%, transparent);
    border-bottom: 1px dashed color-mix(in srgb, var(--accent) 30%, transparent);
    pointer-events: none;
    z-index: 0;
  }

  .hour-cell {
    height: var(--cell);
    border-top: 1px solid var(--border);
  }
  .day-col .hour-cell:first-child { border-top: none; }
  .hour-cell.focused {
    background: color-mix(in srgb, var(--accent) 25%, transparent);
    box-shadow: inset 0 0 0 2px var(--accent);
  }
  .day-col.today .hour-cell.focused {
    background: color-mix(in srgb, var(--today) 25%, transparent);
    box-shadow: inset 0 0 0 2px var(--today);
  }

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
  .block.info,
  .all-day-block.info {
    cursor: default;
  }
  .block.event {
    background: color-mix(in srgb, var(--gcal) 75%, transparent);
    color: var(--gcal-fg);
    border: 1px solid var(--gcal);
    cursor: default;
  }
  .all-day-block.event {
    background: color-mix(in srgb, var(--gcal) 65%, transparent);
    color: var(--gcal-fg);
    border: 1px solid var(--gcal);
    position: relative;
    display: flex;
    align-items: center;
    gap: 4px;
    padding-right: 22px;
  }
  .ev-text {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    flex: 1;
  }
  .ev-badge {
    position: absolute;
    top: 2px;
    right: 3px;
    font-size: 0.55rem;
    font-weight: 700;
    line-height: 1;
    padding: 2px 4px;
    border-radius: 3px;
    background: rgba(255, 255, 255, 0.18);
    color: var(--gcal-fg);
    letter-spacing: 0.04em;
  }
  .block.birthday,
  .all-day-block.birthday {
    background: color-mix(in srgb, #c97a8a 30%, transparent);
    border-color: #c97a8a;
  }
  .block.birthday .ev-badge,
  .all-day-block.birthday .ev-badge {
    background: rgba(201, 122, 138, 0.4);
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
