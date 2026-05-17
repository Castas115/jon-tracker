<script lang="ts">
  import { onMount } from 'svelte';
  import { api, type CalendarEvent, type Idea } from './lib/api';
  import { addMonthsYMD, mondayOf, ymd } from './lib/dates';
  import { isInputFocused, isPlainKey } from './lib/keys';
  import { type Task } from './lib/types';
  import { applyTheme, loadTheme, saveTheme, type Theme } from './lib/theme';
  import BacklogView from './BacklogView.svelte';
  import DayView from './DayView.svelte';
  import FeaturesView from './FeaturesView.svelte';
  import HelpDialog from './HelpDialog.svelte';
  import InboxView from './InboxView.svelte';
  import MonthView from './MonthView.svelte';
  import StreaksView from './StreaksView.svelte';
  import WeekGrid from './WeekGrid.svelte';
  import TaskFormDialog, { type TaskFormValues } from './TaskFormDialog.svelte';

  type View = 'day' | 'week' | 'month' | 'backlog' | 'streaks' | 'inbox' | 'features';

  let tasks = $state<Task[]>([]);
  let ideas = $state<Idea[]>([]);
  let events = $state<CalendarEvent[]>([]);
  let calendarConfigured = $state(false);
  let loading = $state(true);
  let error = $state<string | null>(null);
  let theme = $state<Theme>('dark');
  let view = $state<View>(loadView());

  function loadView(): View {
    const v = localStorage.getItem('tracker-view');
    return v === 'day' ||
      v === 'month' ||
      v === 'week' ||
      v === 'backlog' ||
      v === 'streaks' ||
      v === 'inbox' ||
      v === 'features'
      ? v
      : 'week';
  }
  $effect(() => {
    localStorage.setItem('tracker-view', view);
  });

  type DialogInitial = {
    title?: string;
    description?: string;
    task_type?: 'recurring' | 'single' | 'birthday' | 'weekly_goal';
    weekdays?: number[];
    fixed_date?: string;
    start?: string;
    end?: string;
    is_todo?: boolean;
    target_per_week?: number;
    target_segments?: { weekdays: number[]; target: number }[];
    show_in_upcoming?: boolean;
    start_date?: string;
    end_date?: string;
  };

  let dialogOpen = $state(false);
  let dialogInitial = $state<DialogInitial>({});
  let editingId = $state<number | null>(null);
  let helpOpen = $state(false);

  // Cursor for keyboard navigation. Week: weekday 0..6, hour 6..23.
  // Month: a YYYY-MM-DD pointing at any day in the month grid.
  const _now = new Date();
  const _weekdayMonFirst = (_now.getDay() === 0 ? 6 : _now.getDay() - 1);
  let focusedWeekday = $state(_weekdayMonFirst);
  let focusedHour = $state(Math.min(23, Math.max(6, _now.getHours())));
  let focusedDate = $state(ymd(_now));

  const weekStartYMD = $derived(ymd(mondayOf(new Date(focusedDate + 'T00:00:00'))));

  // Vim-style count prefix: "3j" → repeat j three times.
  let pendingCount = $state('');
  let countTimer: number | null = null;

  // `g` toggles between "today" and the previous focused position.
  let previousFocusedDate: string | null = null;
  function isAtCurrent(): boolean {
    const today = new Date();
    const fd = new Date(focusedDate + 'T00:00:00');
    if (view === 'day') return ymd(today) === focusedDate;
    if (view === 'week') return ymd(mondayOf(today)) === ymd(mondayOf(fd));
    if (view === 'month') {
      return today.getFullYear() === fd.getFullYear() && today.getMonth() === fd.getMonth();
    }
    return false;
  }
  function toggleToday() {
    if (isAtCurrent() && previousFocusedDate) {
      const restore = previousFocusedDate;
      previousFocusedDate = focusedDate;
      focusedDate = restore;
      return;
    }
    previousFocusedDate = focusedDate;
    const now = new Date();
    focusedDate = ymd(now);
    focusedWeekday = now.getDay() === 0 ? 6 : now.getDay() - 1;
    focusedHour = Math.min(23, Math.max(6, now.getHours()));
  }
  function setCount(next: string) {
    pendingCount = next;
    if (countTimer !== null) clearTimeout(countTimer);
    if (next === '') return;
    countTimer = window.setTimeout(() => {
      pendingCount = '';
      countTimer = null;
    }, 1500);
  }

  function toggleTheme() {
    theme = theme === 'dark' ? 'light' : 'dark';
    saveTheme(theme);
  }

  async function load() {
    loading = true;
    error = null;
    try {
      tasks = await api.list();
    } catch (e) {
      error = e instanceof Error ? e.message : String(e);
    } finally {
      loading = false;
    }
  }

  async function refreshCalendar() {
    try {
      const s = await api.calendarStatus();
      calendarConfigured = s.configured;
      if (s.configured) {
        // Fetch a wide-enough window so both week and month views are covered
        // by a single call.
        const now = new Date();
        const from = new Date(now.getFullYear(), now.getMonth(), 1);
        from.setDate(from.getDate() - 14);
        const to = new Date(now.getFullYear(), now.getMonth() + 2, 0);
        events = await api.events(ymd(from), ymd(to));
      } else {
        events = [];
      }
    } catch (e) {
      console.warn('calendar status/events failed', e);
    }
  }

  function openCreate(prefill: DialogInitial = {}) {
    editingId = null;
    dialogInitial = {
      task_type: 'single',
      fixed_date: ymd(new Date()),
      ...prefill
    };
    dialogOpen = true;
  }

  function openEdit(t: Task) {
    editingId = t.id;
    dialogInitial = {
      title: t.title,
      description: t.description ?? undefined,
      task_type: t.task_type,
      weekdays: t.weekdays ?? undefined,
      fixed_date: t.fixed_date ?? undefined,
      start: t.start_time ?? '',
      end: t.end_time ?? '',
      is_todo: t.is_todo,
      target_per_week: t.target_per_week ?? undefined,
      target_segments: t.target_segments ?? undefined,
      show_in_upcoming: t.show_in_upcoming,
      start_date: t.start_date ?? undefined,
      end_date: t.end_date ?? undefined
    };
    dialogOpen = true;
  }

  function closeDialog() {
    dialogOpen = false;
    editingId = null;
  }

  async function submitDialog(v: TaskFormValues) {
    try {
      if (editingId !== null) {
        const updated = await api.update(editingId, v);
        tasks = tasks.map((x) => (x.id === updated.id ? updated : x));
      } else {
        const t = await api.create(v);
        tasks = [...tasks, t];
      }
      dialogOpen = false;
      editingId = null;
    } catch (e) {
      error = e instanceof Error ? e.message : String(e);
    }
  }

  async function toggle(t: Task, date: string, action: 'toggle' | 'add' | 'remove' = 'toggle') {
    try {
      const updated = await api.toggle(t.id, date, action);
      tasks = tasks.map((x) => (x.id === t.id ? updated : x));
    } catch (e) {
      error = e instanceof Error ? e.message : String(e);
    }
  }

  async function remove(t: Task) {
    if (!confirm(`Delete "${t.title}"?`)) return;
    try {
      await api.remove(t.id);
      tasks = tasks.filter((x) => x.id !== t.id);
    } catch (e) {
      error = e instanceof Error ? e.message : String(e);
    }
  }

  async function assignDate(t: Task, dateYMD: string) {
    try {
      const updated = await api.update(t.id, { fixed_date: dateYMD });
      tasks = tasks.map((x) => (x.id === t.id ? updated : x));
    } catch (e) {
      error = e instanceof Error ? e.message : String(e);
    }
  }

  function shiftDate(dateYMD: string, days: number): string {
    const d = new Date(dateYMD + 'T00:00:00');
    d.setDate(d.getDate() + days);
    return ymd(d);
  }

  function handleKey(e: KeyboardEvent) {
    if (isInputFocused()) return;
    if (dialogOpen || helpOpen) return;

    // Escape clears any pending count.
    if (e.key === 'Escape') {
      setCount('');
      return;
    }

    // Digit prefix accumulates count for the next motion. Don't capture a
    // leading "0" (vim convention: 0 is a motion, not a count).
    if (/^[0-9]$/.test(e.key) && !e.ctrlKey && !e.metaKey && !e.altKey) {
      if (pendingCount === '' && e.key === '0') {
        // fall through — 0 with no count is a no-op for now
      } else {
        e.preventDefault();
        setCount(pendingCount + e.key);
        return;
      }
    }

    // Global commands first.
    if (isPlainKey(e, 'n')) {
      e.preventDefault();
      openCreate();
      setCount('');
      return;
    }
    // Alt+H / Alt+L cycle between views.
    const VIEW_ORDER: View[] = ['day', 'week', 'month', 'backlog', 'streaks', 'inbox', 'features'];
    if (e.altKey && !e.ctrlKey && !e.metaKey && (e.key === 'h' || e.key === 'H' || e.key === 'ArrowLeft')) {
      e.preventDefault();
      const i = VIEW_ORDER.indexOf(view);
      view = VIEW_ORDER[Math.max(0, i - 1)];
      setCount('');
      return;
    }
    if (e.altKey && !e.ctrlKey && !e.metaKey && (e.key === 'l' || e.key === 'L' || e.key === 'ArrowRight')) {
      e.preventDefault();
      const i = VIEW_ORDER.indexOf(view);
      view = VIEW_ORDER[Math.min(VIEW_ORDER.length - 1, i + 1)];
      setCount('');
      return;
    }
    if (isPlainKey(e, 't')) {
      e.preventDefault();
      toggleTheme();
      setCount('');
      return;
    }
    if (isPlainKey(e, 'g')) {
      e.preventDefault();
      setCount('');
      toggleToday();
      return;
    }
    if (isPlainKey(e, 'b')) {
      e.preventDefault();
      view = 'backlog';
      setCount('');
      return;
    }
    if (e.key === '?' && !e.ctrlKey && !e.metaKey && !e.altKey) {
      e.preventDefault();
      helpOpen = true;
      setCount('');
      return;
    }
    if (isPlainKey(e, 's')) {
      e.preventDefault();
      view = 'streaks';
      setCount('');
      return;
    }
    if (isPlainKey(e, 'i')) {
      e.preventDefault();
      view = 'inbox';
      setCount('');
      return;
    }
    if (isPlainKey(e, 'f')) {
      e.preventDefault();
      view = 'features';
      setCount('');
      return;
    }

    // Shift+H / Shift+L: jump previous/next week (week+day views) or month (month view).
    // KeyboardEvent.key on shifted letter is uppercase, so plain "H" / "L".
    const isShiftLeft =
      e.shiftKey && !e.ctrlKey && !e.metaKey && !e.altKey && (e.key === 'H' || e.key === 'ArrowLeft');
    const isShiftRight =
      e.shiftKey && !e.ctrlKey && !e.metaKey && !e.altKey && (e.key === 'L' || e.key === 'ArrowRight');

    if (isShiftLeft || isShiftRight) {
      e.preventDefault();
      const count = Math.max(1, parseInt(pendingCount, 10) || 1);
      setCount('');
      const sign = isShiftLeft ? -1 : 1;
      if (view === 'month') {
        focusedDate = addMonthsYMD(focusedDate, sign * count);
      } else {
        // week + day: jump by full weeks
        focusedDate = shiftDate(focusedDate, sign * 7 * count);
      }
      return;
    }

    // Navigation: hjkl + arrows, multiplied by pending count.
    const isLeft = isPlainKey(e, 'h') || isPlainKey(e, 'ArrowLeft');
    const isRight = isPlainKey(e, 'l') || isPlainKey(e, 'ArrowRight');
    const isUp = isPlainKey(e, 'k') || isPlainKey(e, 'ArrowUp');
    const isDown = isPlainKey(e, 'j') || isPlainKey(e, 'ArrowDown');

    if (!isLeft && !isRight && !isUp && !isDown) {
      // Any other key cancels the pending count.
      setCount('');
      return;
    }
    e.preventDefault();

    const count = Math.max(1, parseInt(pendingCount, 10) || 1);
    setCount('');

    if (view === 'week') {
      if (isLeft) focusedWeekday = Math.max(0, focusedWeekday - count);
      else if (isRight) focusedWeekday = Math.min(6, focusedWeekday + count);
      else if (isUp) focusedHour = Math.max(6, focusedHour - count);
      else if (isDown) focusedHour = Math.min(23, focusedHour + count);
    } else {
      // day + month: same date-based motion (h/l ±1 day, j/k ±7 days)
      if (isLeft) focusedDate = shiftDate(focusedDate, -count);
      else if (isRight) focusedDate = shiftDate(focusedDate, count);
      else if (isUp) focusedDate = shiftDate(focusedDate, -7 * count);
      else if (isDown) focusedDate = shiftDate(focusedDate, 7 * count);
    }
  }

  async function loadIdeas() {
    try {
      ideas = await api.listIdeas();
    } catch (e) {
      console.warn('ideas fetch failed', e);
    }
  }

  onMount(() => {
    theme = loadTheme();
    applyTheme(theme);
    load();
    refreshCalendar();
    loadIdeas();
    window.addEventListener('keydown', handleKey);
    return () => window.removeEventListener('keydown', handleKey);
  });
</script>

<main>
  <div class="controls">
    <header>
      <div class="tabs" role="tablist">
        <button
          class="tab"
          class:active={view === 'day'}
          role="tab"
          aria-selected={view === 'day'}
          onclick={() => (view = 'day')}
        >
          Day
        </button>
        <button
          class="tab"
          class:active={view === 'week'}
          role="tab"
          aria-selected={view === 'week'}
          onclick={() => (view = 'week')}
        >
          Week
        </button>
        <button
          class="tab"
          class:active={view === 'month'}
          role="tab"
          aria-selected={view === 'month'}
          onclick={() => (view = 'month')}
        >
          Month
        </button>
        <button
          class="tab"
          class:active={view === 'backlog'}
          role="tab"
          aria-selected={view === 'backlog'}
          onclick={() => (view = 'backlog')}
          title="Backlog (b)"
        >
          Backlog
        </button>
        <button
          class="tab"
          class:active={view === 'streaks'}
          role="tab"
          aria-selected={view === 'streaks'}
          onclick={() => (view = 'streaks')}
          title="Goal streaks (s)"
        >
          Streaks
        </button>
        <button
          class="tab"
          class:active={view === 'inbox'}
          role="tab"
          aria-selected={view === 'inbox'}
          onclick={() => (view = 'inbox')}
          title="Inbox (i)"
        >
          Inbox
        </button>
        <button
          class="tab"
          class:active={view === 'features'}
          role="tab"
          aria-selected={view === 'features'}
          onclick={() => (view = 'features')}
          title="Feature requests (f)"
        >
          Features
        </button>
      </div>
      <div class="header-actions">
        <button
          class="primary"
          type="button"
          onclick={() => openCreate()}
          title="New task (n)"
        >
          + New task
        </button>
        <button
          class="icon"
          type="button"
          aria-label={theme === 'dark' ? 'Day mode' : 'Night mode'}
          title={`${theme === 'dark' ? 'Day mode' : 'Night mode'} (t)`}
          onclick={toggleTheme}
        >
          {theme === 'dark' ? '☀' : '☾'}
        </button>
        <button
          class="icon"
          type="button"
          aria-label="Keyboard shortcuts"
          title="Keyboard shortcuts (?)"
          onclick={() => (helpOpen = true)}
        >
          ?
        </button>
      </div>
    </header>

    {#if error}
      <div class="error">{error}</div>
    {/if}
  </div>

  {#if loading}
    <p class="empty">Loading...</p>
  {:else if view === 'backlog'}
    <BacklogView
      {tasks}
      onToggle={toggle}
      onAssignDate={assignDate}
      onRemove={remove}
      onCreate={() => openCreate({ task_type: 'single', fixed_date: '', is_todo: true })}
      onCreateGoal={() => openCreate({ task_type: 'weekly_goal', is_todo: true, target_per_week: 3 })}
      onEdit={openEdit}
    />
  {:else if view === 'streaks'}
    <StreaksView {tasks} />
  {:else if view === 'inbox'}
    <InboxView {ideas} onChange={(next) => (ideas = next)} />
  {:else if view === 'features'}
    <FeaturesView />
  {:else if view === 'month'}
    <MonthView
      {tasks}
      {events}
      bind:focusedDate
      onToggle={toggle}
      onCreate={(dateYMD) =>
        openCreate({ task_type: 'single', fixed_date: dateYMD, start: '', end: '' })}
    />
  {:else if view === 'day'}
    <DayView
      {tasks}
      {events}
      bind:focusedDate
      onToggle={toggle}
      onRemove={remove}
      onCreate={(weekday, dateYMD, start, end) =>
        openCreate({
          task_type: 'single',
          fixed_date: dateYMD,
          weekdays: [weekday],
          start,
          end
        })}
    />
  {:else}
    <WeekGrid
      {tasks}
      {events}
      {weekStartYMD}
      {focusedWeekday}
      {focusedHour}
      onToggle={toggle}
      onRemove={remove}
      onCreate={(weekday, dateYMD, start, end) =>
        openCreate({
          task_type: 'single',
          fixed_date: dateYMD,
          weekdays: [weekday],
          start,
          end
        })}
    />
  {/if}
</main>

<TaskFormDialog
  open={dialogOpen}
  initial={dialogInitial}
  editing={editingId !== null}
  onSubmit={submitDialog}
  onClose={closeDialog}
/>

<HelpDialog open={helpOpen} {view} onClose={() => (helpOpen = false)} />

<style>
  .header-actions {
    display: flex;
    gap: 0.4rem;
    align-items: center;
  }

  .tabs {
    display: flex;
    gap: 4px;
    background: var(--bg-2);
    padding: 4px;
    border-radius: var(--radius);
    border: 1px solid var(--border);
    flex: 1;
    max-width: 420px;
  }
  .tab {
    flex: 1;
    background: transparent;
    border: none;
    color: var(--fg-muted);
    padding: 0.55rem 0.75rem;
    border-radius: 6px;
    cursor: pointer;
    font: inherit;
    font-size: 0.9rem;
    transition: background-color 100ms ease, color 100ms ease;
  }
  .tab:hover { color: var(--fg); }
  .tab.active {
    background: var(--accent);
    color: var(--accent-fg);
    font-weight: 600;
  }

</style>
