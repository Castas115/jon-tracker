<script lang="ts">
  import { onMount } from 'svelte';
  import { type CalendarEvent } from './lib/api';
  import { MONTH_LABELS, isToday, isoWeekNumber, weekdayMonFirst, ymd } from './lib/dates';
  import { WEEKDAY_LABELS, WEEKDAY_LABELS_LONG, type Task } from './lib/types';
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
  const CELL_PX = 56;

  const WORK_START_MIN = 8 * 60 + 30;
  const WORK_END_MIN = 17 * 60;
  const WORK_TOP_PX = ((WORK_START_MIN - HOUR_START * 60) / 60) * CELL_PX;
  const WORK_HEIGHT_PX = ((WORK_END_MIN - WORK_START_MIN) / 60) * CELL_PX;

  const day = $derived(new Date(focusedDate + 'T00:00:00'));
  const weekday = $derived(weekdayMonFirst(day));
  const dayLabel = $derived(
    `${WEEKDAY_LABELS_LONG[weekday]}, ${day.getDate()} ${MONTH_LABELS[day.getMonth()]} · W${isoWeekNumber(day)}`
  );

  // Live "now" clock — only used to draw the horizontal indicator on today.
  let nowMin = $state(currentMinutes());
  function currentMinutes(): number {
    const n = new Date();
    return n.getHours() * 60 + n.getMinutes();
  }

  let bodyScroll: HTMLDivElement | null = $state(null);
  function centerOnNow() {
    if (!bodyScroll) return;
    const target = ((currentMinutes() - HOUR_START * 60) / 60) * CELL_PX - bodyScroll.clientHeight / 2;
    bodyScroll.scrollTo({ top: Math.max(0, target), behavior: 'auto' });
  }

  onMount(() => {
    const id = window.setInterval(() => (nowMin = currentMinutes()), 60_000);
    // Wait one frame so the layout settles, then center on the current hour.
    requestAnimationFrame(centerOnNow);
    return () => clearInterval(id);
  });

  // When the user navigates to a new day, re-center if it's today; otherwise
  // pin to the top so they don't see a mid-day scroll position from earlier.
  $effect(() => {
    if (!bodyScroll) return;
    void focusedDate;
    if (isToday(day)) {
      requestAnimationFrame(centerOnNow);
    } else {
      bodyScroll.scrollTo({ top: 0, behavior: 'auto' });
    }
  });

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

  function matchesOn(t: Task, dayDate: Date, k: string): boolean {
    const wd = weekdayMonFirst(dayDate);
    if (t.task_type === 'recurring') return (t.weekdays ?? []).includes(wd);
    if (t.task_type === 'single') return t.fixed_date === k;
    if (t.task_type === 'birthday' && t.fixed_date) {
      const bd = new Date(t.fixed_date + 'T00:00:00');
      return bd.getMonth() === dayDate.getMonth() && bd.getDate() === dayDate.getDate();
    }
    if (t.task_type === 'weekly_goal') {
      const segs = t.target_segments ?? [];
      if (segs.length === 0) return true;
      return segs.some((s) => s.weekdays.includes(wd));
    }
    return false;
  }

  function matches(t: Task, k: string): boolean {
    return matchesOn(t, day, k);
  }

  function displayTitle(t: Task, dateYMD: string): string {
    if (t.task_type === 'weekly_goal') return weeklyGoalLabel(t, dateYMD);
    return t.title;
  }

  const tasksToday = $derived(tasks.filter((t) => matches(t, focusedDate)));
  const birthdaysToday = $derived(
    tasksToday.filter((t) => t.task_type === 'birthday')
  );
  // All-day strip excludes birthdays (rendered in their own strip) and includes
  // tasks without a time + ICS all-day events.
  const allDayTasks = $derived(
    tasksToday
      .filter((t) => !t.start_time && t.task_type !== 'birthday')
      .sort((a, b) => a.title.localeCompare(b.title))
  );
  const timedTasks = $derived(
    tasksToday
      .filter((t) => t.start_time)
      .sort((a, b) => (a.start_time ?? '').localeCompare(b.start_time ?? ''))
  );

  // Today's checklist on the sidebar = actionable tasks for the focused day.
  // Weekly goals are excluded since they're handled on the backlog side.
  const checklist = $derived(
    tasksToday
      .filter((t) => t.is_todo && t.task_type !== 'weekly_goal')
      .sort((a, b) => {
        const ta = a.start_time ?? '99:99';
        const tb = b.start_time ?? '99:99';
        return ta.localeCompare(tb) || a.title.localeCompare(b.title);
      })
  );
  const doneCount = $derived(checklist.filter((t) => isDone(t, focusedDate)).length);

  type ParsedEvent = {
    id: string;
    title: string;
    kind: 'event' | 'birthday';
    all_day: boolean;
    start_min: number | null;
    end_min: number | null;
  };

  function parseEventsFor(dayK: string): ParsedEvent[] {
    const out: ParsedEvent[] = [];
    for (const ev of events) {
      let evDateK: string;
      if (ev.all_day) evDateK = ev.start;
      else evDateK = ymd(new Date(ev.start));
      if (evDateK !== dayK) continue;

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

  const evsToday = $derived(parseEventsFor(focusedDate));
  const birthdayEvsToday = $derived(evsToday.filter((e) => e.kind === 'birthday'));
  const allDayEvs = $derived(evsToday.filter((e) => e.all_day && e.kind !== 'birthday'));
  const timedEvs = $derived(
    evsToday
      .filter((e) => !e.all_day)
      .sort((a, b) => (a.start_min ?? 0) - (b.start_min ?? 0))
  );

  // Upcoming: next 7 days (excluding today). Skip empty days.
  type UpcomingItem = {
    kind: 'task' | 'event';
    title: string;
    time: string;
    sortKey: string;
  };
  type UpcomingDay = { date: Date; ymd: string; weekday: string; items: UpcomingItem[] };

  const upcoming = $derived.by<UpcomingDay[]>(() => {
    const out: UpcomingDay[] = [];
    const base = new Date(focusedDate + 'T00:00:00');
    for (let i = 1; i <= 7; i++) {
      const d = new Date(base);
      d.setDate(base.getDate() + i);
      const k = ymd(d);
      const items: UpcomingItem[] = [];
      for (const t of tasks) {
        if (!matchesOn(t, d, k)) continue;
        items.push({
          kind: 'task',
          title: displayTitle(t, k),
          time: t.start_time ?? '',
          sortKey: t.start_time ?? '99:99'
        });
      }
      for (const ev of parseEventsFor(k)) {
        items.push({
          kind: 'event',
          title: (ev.kind === 'birthday' ? '🎂 ' : '') + ev.title,
          time: ev.all_day
            ? ''
            : `${String(Math.floor((ev.start_min ?? 0) / 60)).padStart(2, '0')}:${String((ev.start_min ?? 0) % 60).padStart(2, '0')}`,
          sortKey: ev.all_day
            ? ''
            : `${String(Math.floor((ev.start_min ?? 0) / 60)).padStart(2, '0')}:${String((ev.start_min ?? 0) % 60).padStart(2, '0')}`
        });
      }
      if (items.length === 0) continue;
      items.sort((a, b) => a.sortKey.localeCompare(b.sortKey) || a.title.localeCompare(b.title));
      out.push({
        date: d,
        ymd: k,
        weekday: WEEKDAY_LABELS[weekdayMonFirst(d)],
        items
      });
    }
    return out;
  });

  // Backlog snippet: top 5 undated singles still pending.
  const backlog = $derived(
    tasks
      .filter(
        (t) =>
          t.task_type === 'single' &&
          t.fixed_date === null &&
          t.is_todo &&
          t.completed_dates.length === 0
      )
      .sort((a, b) => b.created_at.localeCompare(a.created_at))
      .slice(0, 5)
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
  const showNow = $derived(
    isToday(day) && nowMin >= HOUR_START * 60 && nowMin < HOUR_END * 60
  );
  const nowTop = $derived(((nowMin - HOUR_START * 60) / 60) * CELL_PX);
  const todayDoneSummary = $derived(`${doneCount}/${checklist.length}`);
</script>

<section class="day-wrap">
  <div class="nav">
    <button class="icon" type="button" aria-label="Previous day" onclick={() => shift(-1)}>‹</button>
    <strong class:today={isToday(day)}>{dayLabel}</strong>
    <button class="icon" type="button" aria-label="Next day" onclick={() => shift(1)}>›</button>
    <button class="icon today-btn" type="button" onclick={goToday}>Today</button>
  </div>

  {#if birthdaysToday.length > 0 || birthdayEvsToday.length > 0}
    <div class="bday-strip">
      <span class="bday-icon">🎂</span>
      <span class="bday-titles">
        {[
          ...birthdaysToday.map((b) => b.title),
          ...birthdayEvsToday.map((b) => b.title)
        ].join(' · ')}
      </span>
    </div>
  {/if}

  <div class="all-day">
    <span class="time-col tag">All day</span>
    <div class="all-day-cell">
      {#each allDayTasks as t (t.id)}
        {@const done = t.is_todo && t.task_type !== 'weekly_goal' && isDone(t, focusedDate)}
        <button
          class="all-day-block"
          class:done
          class:info={!t.is_todo}
          type="button"
          onclick={() => t.is_todo && onToggle(t, focusedDate)}
          oncontextmenu={(e) => {
            e.preventDefault();
            onRemove(t);
          }}
          title={displayTitle(t, focusedDate)}
        >
          {displayTitle(t, focusedDate)}
        </button>
      {/each}
      {#each allDayEvs as ev (ev.id)}
        <div class="all-day-block event" title={`${ev.title} (Google Calendar)`}>
          <span class="ev-text">{ev.title}</span>
          <span class="ev-badge" aria-label="Google">G</span>
        </div>
      {/each}
      {#if allDayTasks.length === 0 && allDayEvs.length === 0}
        <span class="empty-inline">—</span>
      {/if}
    </div>
  </div>

  <div class="main">
    <div class="body-scroll" bind:this={bodyScroll}>
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

        {#if showNow}
          <div class="now-line" style:top="{nowTop}px" aria-hidden="true"></div>
        {/if}
      </div>
    </div>
    </div>

    <aside class="sidebar">
      <article class="panel">
        <h3>To do <span class="badge">{todayDoneSummary}</span></h3>
        {#if checklist.length === 0}
          <p class="empty">Nothing to tick off.</p>
        {:else}
          <ul>
            {#each checklist as t (t.id)}
              {@const done = isDone(t, focusedDate)}
              <li class:done>
                <label>
                  <input
                    type="checkbox"
                    checked={done}
                    onchange={() => onToggle(t, focusedDate)}
                  />
                  <span class="t-title">{t.title}</span>
                  {#if t.start_time}<span class="t-time">{t.start_time}</span>{/if}
                </label>
              </li>
            {/each}
          </ul>
        {/if}
      </article>

      <article class="panel">
        <h3>Upcoming</h3>
        {#if upcoming.length === 0}
          <p class="empty">Nothing in the next week.</p>
        {:else}
          {#each upcoming as u}
            <div class="up-day">
              <button
                class="up-date"
                type="button"
                onclick={() => (focusedDate = u.ymd)}
                title="Jump to this day"
              >
                {u.weekday} {u.date.getDate()} {MONTH_LABELS[u.date.getMonth()].slice(0, 3)}
              </button>
              <ul class="up-items">
                {#each u.items as it}
                  <li class="up-item up-{it.kind}">
                    {#if it.time}<span class="up-t">{it.time}</span>{/if}
                    <span class="up-title">{it.title}</span>
                  </li>
                {/each}
              </ul>
            </div>
          {/each}
        {/if}
      </article>

      <article class="panel">
        <h3>Backlog</h3>
        {#if backlog.length === 0}
          <p class="empty">Inbox clear.</p>
        {:else}
          <ul>
            {#each backlog as t (t.id)}
              <li>
                <label>
                  <input
                    type="checkbox"
                    onchange={() => onToggle(t, ymd(new Date()))}
                  />
                  <span class="t-title">{t.title}</span>
                </label>
              </li>
            {/each}
          </ul>
        {/if}
      </article>
    </aside>
  </div>
</section>

<style>
  .day-wrap {
    border: 1px solid var(--border);
    border-radius: var(--radius);
    background: var(--bg-2);
    overflow: hidden;
    display: flex;
    flex-direction: column;
    flex: 1;
    min-height: 0;
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

  .bday-strip {
    display: flex;
    align-items: center;
    gap: 0.5rem;
    padding: 0.5rem 0.75rem;
    background: color-mix(in srgb, #c97a8a 18%, transparent);
    border-bottom: 1px solid var(--border);
    font-size: 0.85rem;
  }
  .bday-icon { font-size: 1.1rem; }
  .bday-titles { color: var(--fg); }

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
  .empty-inline { color: var(--fg-muted); font-size: 0.75rem; padding: 2px 4px; }

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

  /* Main row: grid (hours + day-col) on the left, sidebar on the right. */
  .main {
    display: grid;
    grid-template-columns: minmax(0, 1fr) 320px;
    flex: 1;
    min-height: 0;
  }

  .body-scroll {
    overflow-y: auto;
    min-height: 0;
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

  .now-line {
    position: absolute;
    left: 0;
    right: 0;
    height: 2px;
    background: var(--danger);
    pointer-events: none;
    z-index: 3;
    box-shadow: 0 0 6px color-mix(in srgb, var(--danger) 50%, transparent);
  }
  .now-line::before {
    content: '';
    position: absolute;
    left: -5px;
    top: -4px;
    width: 10px;
    height: 10px;
    border-radius: 50%;
    background: var(--danger);
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

  /* Sidebar */
  .sidebar {
    border-left: 1px solid var(--border);
    background: var(--bg-2);
    padding: 0.75rem;
    display: flex;
    flex-direction: column;
    gap: 1rem;
    overflow-y: auto;
    min-height: 0;
  }
  .panel {
    background: var(--bg);
    border: 1px solid var(--border);
    border-radius: 8px;
    padding: 0.6rem 0.75rem;
  }
  .panel h3 {
    margin: 0 0 0.4rem;
    font-size: 0.72rem;
    font-weight: 600;
    text-transform: uppercase;
    letter-spacing: 0.08em;
    color: var(--fg-muted);
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 0.5rem;
  }
  .panel .badge {
    background: var(--bg-3);
    color: var(--fg);
    border-radius: 999px;
    padding: 1px 7px;
    font-size: 0.7rem;
    font-variant-numeric: tabular-nums;
    text-transform: none;
    letter-spacing: 0;
  }
  .panel .empty {
    margin: 0;
    color: var(--fg-muted);
    font-size: 0.8rem;
  }
  .panel ul {
    list-style: none;
    margin: 0;
    padding: 0;
    display: flex;
    flex-direction: column;
    gap: 0.3rem;
  }
  .panel li {
    display: flex;
    align-items: center;
    gap: 0.4rem;
    font-size: 0.85rem;
  }
  .panel label {
    display: flex;
    align-items: center;
    gap: 0.5rem;
    flex: 1;
    cursor: pointer;
  }
  .panel input[type='checkbox'] {
    width: 18px;
    height: 18px;
    accent-color: var(--accent);
    flex: none;
  }
  .panel li.done .t-title {
    color: var(--done);
    text-decoration: line-through;
  }
  .t-title { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  .t-time {
    font-size: 0.75rem;
    color: var(--fg-muted);
    font-variant-numeric: tabular-nums;
  }

  .up-day {
    margin-bottom: 0.55rem;
  }
  .up-day:last-child { margin-bottom: 0; }
  .up-date {
    background: transparent;
    border: none;
    color: var(--fg-muted);
    font: inherit;
    font-size: 0.72rem;
    font-weight: 600;
    text-transform: uppercase;
    letter-spacing: 0.06em;
    padding: 2px 0;
    cursor: pointer;
  }
  .up-date:hover { color: var(--accent); }
  .up-items {
    margin: 0.15rem 0 0;
    padding-left: 0.4rem;
    border-left: 1px solid var(--border);
  }
  .up-item {
    font-size: 0.8rem;
    padding: 1px 0 1px 0.5rem;
    color: var(--fg);
  }
  .up-item.up-event { color: var(--gcal); }
  .up-t {
    font-variant-numeric: tabular-nums;
    color: var(--fg-muted);
    margin-right: 0.3rem;
  }

  @media (max-width: 960px) {
    .day-wrap {
      max-height: none;
    }
    .main {
      grid-template-columns: 1fr;
    }
    .body-scroll {
      overflow-y: visible;
    }
    .sidebar {
      border-left: none;
      border-top: 1px solid var(--border);
      overflow-y: visible;
    }
  }
</style>
