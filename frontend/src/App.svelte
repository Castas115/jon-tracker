<script lang="ts">
  import { onMount } from 'svelte';
  import { api, type CalendarEvent } from './lib/api';
  import { ymd } from './lib/dates';
  import { isInputFocused, isPlainKey } from './lib/keys';
  import { type Task } from './lib/types';
  import { applyTheme, loadTheme, saveTheme, type Theme } from './lib/theme';
  import HelpDialog from './HelpDialog.svelte';
  import MonthView from './MonthView.svelte';
  import WeekGrid from './WeekGrid.svelte';
  import TaskFormDialog, { type TaskFormValues } from './TaskFormDialog.svelte';

  type View = 'week' | 'month';

  let tasks = $state<Task[]>([]);
  let events = $state<CalendarEvent[]>([]);
  let calendarConfigured = $state(false);
  let loading = $state(true);
  let error = $state<string | null>(null);
  let theme = $state<Theme>('dark');
  let view = $state<View>(loadView());

  function loadView(): View {
    const v = localStorage.getItem('tracker-view');
    return v === 'month' || v === 'week' ? v : 'week';
  }
  $effect(() => {
    localStorage.setItem('tracker-view', view);
  });

  type DialogInitial = {
    title?: string;
    task_type?: 'recurring' | 'fixed';
    weekdays?: number[];
    fixed_date?: string;
    start?: string;
    end?: string;
  };

  let dialogOpen = $state(false);
  let dialogInitial = $state<DialogInitial>({});
  let helpOpen = $state(false);

  // Cursor for keyboard navigation. Week: weekday 0..6, hour 6..23.
  // Month: a YYYY-MM-DD pointing at any day in the month grid.
  const _now = new Date();
  const _weekdayMonFirst = (_now.getDay() === 0 ? 6 : _now.getDay() - 1);
  let focusedWeekday = $state(_weekdayMonFirst);
  let focusedHour = $state(Math.min(23, Math.max(6, _now.getHours())));
  let focusedDate = $state(ymd(_now));

  // Vim-style count prefix: "3j" → repeat j three times.
  let pendingCount = $state('');
  let countTimer: number | null = null;
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
    dialogInitial = {
      task_type: 'fixed',
      fixed_date: ymd(new Date()),
      ...prefill
    };
    dialogOpen = true;
  }

  function closeDialog() {
    dialogOpen = false;
  }

  async function submitDialog(v: TaskFormValues) {
    try {
      const t = await api.create(v);
      tasks = [...tasks, t];
      dialogOpen = false;
    } catch (e) {
      error = e instanceof Error ? e.message : String(e);
    }
  }

  async function toggle(t: Task, date: string) {
    try {
      const updated = await api.toggle(t.id, date);
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
    if (isPlainKey(e, 'c') || isPlainKey(e, 'n')) {
      e.preventDefault();
      openCreate();
      setCount('');
      return;
    }
    if (isPlainKey(e, 'w')) {
      e.preventDefault();
      view = 'week';
      setCount('');
      return;
    }
    if (isPlainKey(e, 'm')) {
      e.preventDefault();
      view = 'month';
      setCount('');
      return;
    }
    if (isPlainKey(e, 't')) {
      e.preventDefault();
      toggleTheme();
      setCount('');
      return;
    }
    if (e.key === '?' && !e.ctrlKey && !e.metaKey && !e.altKey) {
      e.preventDefault();
      helpOpen = true;
      setCount('');
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
      if (isLeft) focusedDate = shiftDate(focusedDate, -count);
      else if (isRight) focusedDate = shiftDate(focusedDate, count);
      else if (isUp) focusedDate = shiftDate(focusedDate, -7 * count);
      else if (isDown) focusedDate = shiftDate(focusedDate, 7 * count);
    }
  }

  onMount(() => {
    theme = loadTheme();
    applyTheme(theme);
    load();
    refreshCalendar();
    window.addEventListener('keydown', handleKey);
    return () => window.removeEventListener('keydown', handleKey);
  });
</script>

<main>
  <div class="controls">
    <header>
      <h1>Jon Tracker</h1>
      <div class="header-actions">
        {#if calendarConfigured}
          <span class="cal-badge" title="Google Calendar feed connected">📅</span>
        {/if}
        <button
          class="primary"
          type="button"
          onclick={() => openCreate()}
          title="New task (c)"
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

    <div class="tabs" role="tablist">
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
    </div>

    {#if error}
      <div class="error">{error}</div>
    {/if}
  </div>

  {#if loading}
    <p class="empty">Loading...</p>
  {:else if view === 'month'}
    <MonthView
      {tasks}
      {events}
      bind:focusedDate
      onToggle={toggle}
      onCreate={(dateYMD) =>
        openCreate({ task_type: 'fixed', fixed_date: dateYMD, start: '', end: '' })}
    />
  {:else}
    <WeekGrid
      {tasks}
      {events}
      {focusedWeekday}
      {focusedHour}
      onToggle={toggle}
      onRemove={remove}
      onCreate={(weekday, dateYMD, start, end) =>
        openCreate({
          task_type: 'fixed',
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
  onSubmit={submitDialog}
  onClose={closeDialog}
/>

<HelpDialog open={helpOpen} onClose={() => (helpOpen = false)} />

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
    margin-bottom: 0.75rem;
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

  .cal-badge {
    font-size: 1.1rem;
    line-height: 1;
    padding: 0.35rem 0.5rem;
  }
</style>
