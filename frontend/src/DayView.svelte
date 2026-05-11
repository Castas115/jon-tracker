<script lang="ts">
  import { type CalendarEvent } from './lib/api';
  import { MONTH_LABELS, isToday, isoWeekNumber, weekdayMonFirst, ymd } from './lib/dates';
  import { WEEKDAY_LABELS_LONG, type Task } from './lib/types';
  import { weeklyGoalLabel } from './lib/weeklyGoal';

  type Props = {
    tasks: Task[];
    events?: CalendarEvent[];
    focusedDate?: string;
    onToggle: (task: Task, dateYMD: string) => void;
    onRemove: (task: Task) => void;
    onCreate: (weekday: number, dateYMD: string, start: string, end: string) => void;
  };

  let {
    tasks,
    events = [],
    focusedDate = $bindable(ymd(new Date())),
    onToggle,
    onRemove,
    onCreate
  }: Props = $props();

  const HOUR_START = 6;
  const HOUR_END = 24;
  const HOURS = Array.from({ length: HOUR_END - HOUR_START }, (_, i) => HOUR_START + i);
  const CELL_PX = 56; // a touch taller than week view since we have horizontal room

  const WORK_START_MIN = 8 * 60 + 30;
  const WORK_END_MIN = 17 * 60;
  const WORK_TOP_PX = ((WORK_START_MIN - HOUR_START * 60) / 60) * CELL_PX;
  const WORK_HEIGHT_PX = ((WORK_END_MIN - WORK_START_MIN) / 60) * CELL_PX;

  const day = $derived(new Date(focusedDate + 'T00:00:00'));
  const weekday = $derived(weekdayMonFirst(day));
  const dayLabel = $derived(
    `${WEEKDAY_LABELS_LONG[weekday]}, ${day.getDate()} ${MONTH_LABELS[day.getMonth()]} · W${isoWeekNumber(day)}`
  );

  function shift(delta: number) {
    const d = new Date(focusedDate + 'T00:00:00');
    d.setDate(d.getDate() + delta);
    focusedDate = ymd(d);
  }
  function goToday() {
    focusedDate = ymd(new Date());
  }

  function toMinutes(t: string | null): number | null {
    if (!t) return null;
    const [h, m] = t.split(':').map(Number);
    return h * 60 + m;
  }
  function isDone(t: Task, k: string): boolean {
    return t.completed_dates.includes(k);
  }
  function fmtTime(t: Task): string {
    if (!t.start_time) return '';
    return t.end_time ? `${t.start_time}–${t.end_time}` : t.start_time;
  }
  function blockStyle(start: number, end: number): string {
    const top = ((start - HOUR_START * 60) / 60) * CELL_PX;
    const height = ((end - start) / 60) * CELL_PX;
    return `top: ${Math.max(0, top)}px; height: ${Math.max(20, height - 2)}px;`;
  }

  function matches(t: Task, k: string): boolean {
    if (t.task_type === 'recurring') return (t.weekdays ?? []).includes(weekday);
    if (t.task_type === 'single') return t.fixed_date === k;
    if (t.task_type === 'birthday' && t.fixed_date) {
      const bd = new Date(t.fixed_date + 'T00:00:00');
      return bd.getMonth() === day.getMonth() && bd.getDate() === day.getDate();
    }
    if (t.task_type === 'weekly_goal') {
      return t.completed_dates.includes(k);
    }
    return false;
  }

  function displayTitle(t: Task, dateYMD: string): string {
    if (t.task_type === 'weekly_goal') return weeklyGoalLabel(t, dateYMD);
    return t.title;
  }

  const tasksToday = $derived(tasks.filter((t) => matches(t, focusedDate)));
  const allDayTasks = $derived(
    tasksToday
      .filter((t) => !t.start_time)
      .sort((a, b) => a.title.localeCompare(b.title))
  );
  const timedTasks = $derived(
    tasksToday
      .filter((t) => t.start_time)
      .sort((a, b) => (a.start_time ?? '').localeCompare(b.start_time ?? ''))
  );

  type ParsedEvent = {
    id: string;
    title: string;
    kind: 'event' | 'birthday';
    all_day: boolean;
    start_min: number | null;
    end_min: number | null;
  };

  function parseEvents(): ParsedEvent[] {
    const k = focusedDate;
    const out: ParsedEvent[] = [];
    for (const ev of events) {
      let evDateK: string;
      if (ev.all_day) evDateK = ev.start;
      else evDateK = ymd(new Date(ev.start));
      if (evDateK !== k) continue;

      if (ev.all_day) {
        out.push({
          id: ev.id,
          title: ev.title,
          kind: ev.kind,
          all_day: true,
          start_min: null,
          end_min: null
        });
      } else {
        const s = new Date(ev.start);
        const e = ev.end ? new Date(ev.end) : null;
        const startMin = s.getHours() * 60 + s.getMinutes();
        const endMin = e ? e.getHours() * 60 + e.getMinutes() : startMin + 60;
        out.push({
          id: ev.id,
          title: ev.title,
          kind: ev.kind,
          all_day: false,
          start_min: startMin,
          end_min: endMin
        });
      }
    }
    return out;
  }

  const evsToday = $derived(parseEvents());
  const allDayEvs = $derived(evsToday.filter((e) => e.all_day));
  const timedEvs = $derived(
    evsToday
      .filter((e) => !e.all_day)
      .sort((a, b) => (a.start_min ?? 0) - (b.start_min ?? 0))
  );

  function handleColClick(e: MouseEvent) {
    const target = e.target as HTMLElement;
    if (target.closest('.block')) return;
    const rect = (e.currentTarget as HTMLElement).getBoundingClientRect();
    const y = e.clientY - rect.top;
    let hour = Math.floor(y / CELL_PX) + HOUR_START;
    if (hour < HOUR_START) hour = HOUR_START;
    if (hour >= HOUR_END - 1) hour = HOUR_END - 2;
    const pad = (n: number) => String(n).padStart(2, '0');
    onCreate(weekday, focusedDate, `${pad(hour)}:00`, `${pad(hour + 1)}:00`);
  }

  const gridHeight = (HOUR_END - HOUR_START) * CELL_PX;
</script>

<section class="day-wrap">
  <div class="nav">
    <button class="icon" type="button" aria-label="Previous day" onclick={() => shift(-1)}>‹</button>
    <strong class:today={isToday(day)}>{dayLabel}</strong>
    <button class="icon" type="button" aria-label="Next day" onclick={() => shift(1)}>›</button>
    <button class="icon today-btn" type="button" onclick={goToday}>Today</button>
  </div>

  <div class="all-day">
    <span class="time-col tag">All day</span>
    <div class="all-day-cell">
      {#each allDayTasks as t (t.id)}
        {@const done = t.is_todo && t.task_type !== 'weekly_goal' && isDone(t, focusedDate)}
        <button
          class="all-day-block"
          class:done
          class:info={!t.is_todo}
          class:bday-task={t.task_type === 'birthday'}
          type="button"
          onclick={() => t.is_todo && onToggle(t, focusedDate)}
          oncontextmenu={(e) => {
            e.preventDefault();
            onRemove(t);
          }}
          title={`${t.task_type === 'birthday' ? '🎂 ' : ''}${displayTitle(t, focusedDate)}`}
        >
          {t.task_type === 'birthday' ? '🎂 ' : ''}{displayTitle(t, focusedDate)}
        </button>
      {/each}
      {#each allDayEvs as ev (ev.id)}
        <div
          class="all-day-block event"
          class:birthday={ev.kind === 'birthday'}
          title={`${ev.kind === 'birthday' ? '🎂 ' : ''}${ev.title} (Google Calendar)`}
        >
          <span class="ev-text">{ev.kind === 'birthday' ? '🎂 ' : ''}{ev.title}</span>
          <span class="ev-badge" aria-label="Google">G</span>
        </div>
      {/each}
    </div>
  </div>

  <div class="body" style:--cell="{CELL_PX}px" style:height="{gridHeight}px">
    <div class="hours">
      {#each HOURS as h}
        <span class="hour-label">{String(h).padStart(2, '0')}:00</span>
      {/each}
    </div>

    <div
      class="day-col"
      role="button"
      tabindex="0"
      aria-label="Add task"
      onclick={handleColClick}
      onkeydown={(e) => {
        if (e.key === 'Enter' || e.key === ' ') {
          e.preventDefault();
          handleColClick(e as unknown as MouseEvent);
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

      {#each HOURS as _h}
        <div class="hour-cell"></div>
      {/each}

      {#each timedTasks as t (t.id)}
        {@const sm = toMinutes(t.start_time)}
        {@const em = toMinutes(t.end_time) ?? (sm !== null ? sm + 60 : null)}
        {#if sm !== null && em !== null}
          {@const done = t.is_todo && isDone(t, focusedDate)}
          <button
            class="block"
            class:done
            class:info={!t.is_todo}
            type="button"
            style={blockStyle(sm, em)}
            onclick={() => t.is_todo && onToggle(t, focusedDate)}
            oncontextmenu={(e) => {
              e.preventDefault();
              onRemove(t);
            }}
            title={`${t.title} ${fmtTime(t)}${t.is_todo ? ' (click: toggle · right-click: delete)' : ' (right-click: delete)'}`}
          >
            <span class="b-title">{t.title}</span>
            <span class="b-time">{fmtTime(t)}</span>
          </button>
        {/if}
      {/each}

      {#each timedEvs as ev (ev.id)}
        {#if ev.start_min !== null && ev.end_min !== null}
          <div
            class="block event"
            class:birthday={ev.kind === 'birthday'}
            style={blockStyle(ev.start_min, ev.end_min)}
            title={`${ev.kind === 'birthday' ? '🎂 ' : ''}${ev.title} (Google Calendar)`}
          >
            <span class="b-title">
              {ev.kind === 'birthday' ? '🎂 ' : ''}{ev.title}
            </span>
            <span class="ev-badge" aria-label="Google">G</span>
          </div>
        {/if}
      {/each}
    </div>
  </div>
</section>

<style>
  .day-wrap {
    border: 1px solid var(--border);
    border-radius: var(--radius);
    background: var(--bg-2);
    overflow: hidden;
  }

  .nav {
    display: flex;
    align-items: center;
    gap: 0.5rem;
    padding: 0.5rem 0.75rem;
    border-bottom: 1px solid var(--border);
  }
  .nav strong {
    flex: 1;
    text-align: center;
    font-size: 1rem;
    text-transform: capitalize;
  }
  .nav strong.today { color: var(--today); }
  .today-btn {
    font-size: 0.75rem;
    padding: 0.3rem 0.6rem;
    border: 1px solid var(--border);
  }

  .all-day {
    display: grid;
    grid-template-columns: 70px 1fr;
    border-bottom: 1px solid var(--border);
  }
  .time-col { display: block; }
  .tag {
    font-size: 0.7rem;
    color: var(--fg-muted);
    padding: 0.5rem 0.5rem;
    text-align: right;
    line-height: 1.1;
  }
  .all-day-cell {
    border-left: 1px solid var(--border);
    padding: 6px;
    min-height: 32px;
    display: flex;
    flex-direction: column;
    gap: 4px;
  }
  .all-day-block {
    background: color-mix(in srgb, var(--accent) 75%, transparent);
    color: var(--accent-fg);
    border: none;
    border-radius: 4px;
    padding: 4px 8px;
    font-size: 0.78rem;
    font: inherit;
    cursor: pointer;
    text-align: left;
  }
  .all-day-block.done { opacity: 0.45; text-decoration: line-through; }
  .all-day-block.event {
    background: color-mix(in srgb, var(--gcal) 65%, transparent);
    color: var(--gcal-fg);
    border: 1px solid var(--gcal);
    position: relative;
    display: flex;
    align-items: center;
    padding-right: 26px;
  }
  .all-day-block.birthday {
    background: color-mix(in srgb, #c97a8a 30%, transparent);
    border-color: #c97a8a;
    color: var(--fg);
  }
  .ev-text {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    flex: 1;
  }
  .ev-badge {
    position: absolute;
    top: 4px;
    right: 4px;
    font-size: 0.6rem;
    font-weight: 700;
    line-height: 1;
    padding: 2px 4px;
    border-radius: 3px;
    background: rgba(255, 255, 255, 0.18);
    color: var(--gcal-fg);
    letter-spacing: 0.04em;
  }

  .body {
    display: grid;
    grid-template-columns: 70px 1fr;
    position: relative;
  }

  .hours {
    display: grid;
    grid-template-rows: repeat(18, var(--cell));
    border-right: 1px solid var(--border);
  }
  .hour-label {
    font-size: 0.7rem;
    color: var(--fg-muted);
    padding: 2px 6px 0 0;
    text-align: right;
    border-top: 1px solid var(--border);
    height: var(--cell);
    box-sizing: border-box;
  }
  .hour-label:first-child { border-top: none; }

  .day-col {
    position: relative;
    border-left: 1px solid var(--border);
    cursor: pointer;
  }
  .hour-cell {
    height: var(--cell);
    border-top: 1px solid var(--border);
  }
  .day-col .hour-cell:first-child { border-top: none; }

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

  .block {
    position: absolute;
    left: 4px;
    right: 4px;
    background: var(--accent);
    color: var(--accent-fg);
    border: none;
    border-radius: 6px;
    padding: 6px 8px;
    cursor: pointer;
    text-align: left;
    overflow: hidden;
    font: inherit;
    display: flex;
    flex-direction: column;
    gap: 2px;
  }
  .block.done { opacity: 0.45; text-decoration: line-through; }
  .block.info, .all-day-block.info { cursor: default; }
  .block.event {
    background: color-mix(in srgb, var(--gcal) 75%, transparent);
    color: var(--gcal-fg);
    border: 1px solid var(--gcal);
    cursor: default;
  }
  .block.birthday {
    background: color-mix(in srgb, #c97a8a 30%, transparent);
    border-color: #c97a8a;
    color: var(--fg);
  }
  .b-title { font-size: 0.85rem; font-weight: 600; line-height: 1.2; }
  .b-time { font-size: 0.7rem; opacity: 0.85; line-height: 1; }
</style>
