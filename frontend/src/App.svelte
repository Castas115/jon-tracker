<script lang="ts">
  import { onMount } from 'svelte';
  import { api } from './lib/api';
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
  let loading = $state(true);
  let error = $state<string | null>(null);
  let theme = $state<Theme>('dark');
  let view = $state<View>('week');

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

    // Global commands first.
    if (isPlainKey(e, 'c') || isPlainKey(e, 'n')) {
      e.preventDefault();
      openCreate();
      return;
    }
    if (isPlainKey(e, 'w')) {
      e.preventDefault();
      view = 'week';
      return;
    }
    if (isPlainKey(e, 'm')) {
      e.preventDefault();
      view = 'month';
      return;
    }
    if (isPlainKey(e, 't')) {
      e.preventDefault();
      toggleTheme();
      return;
    }
    if (e.key === '?' && !e.ctrlKey && !e.metaKey && !e.altKey) {
      e.preventDefault();
      helpOpen = true;
      return;
    }

    // Navigation: hjkl + arrows.
    const isLeft = isPlainKey(e, 'h') || isPlainKey(e, 'ArrowLeft');
    const isRight = isPlainKey(e, 'l') || isPlainKey(e, 'ArrowRight');
    const isUp = isPlainKey(e, 'k') || isPlainKey(e, 'ArrowUp');
    const isDown = isPlainKey(e, 'j') || isPlainKey(e, 'ArrowDown');

    if (!isLeft && !isRight && !isUp && !isDown) return;
    e.preventDefault();

    if (view === 'week') {
      if (isLeft) focusedWeekday = Math.max(0, focusedWeekday - 1);
      else if (isRight) focusedWeekday = Math.min(6, focusedWeekday + 1);
      else if (isUp) focusedHour = Math.max(6, focusedHour - 1);
      else if (isDown) focusedHour = Math.min(23, focusedHour + 1);
    } else {
      if (isLeft) focusedDate = shiftDate(focusedDate, -1);
      else if (isRight) focusedDate = shiftDate(focusedDate, 1);
      else if (isUp) focusedDate = shiftDate(focusedDate, -7);
      else if (isDown) focusedDate = shiftDate(focusedDate, 7);
    }
  }

  onMount(() => {
    theme = loadTheme();
    applyTheme(theme);
    load();
    window.addEventListener('keydown', handleKey);
    return () => window.removeEventListener('keydown', handleKey);
  });
</script>

<main>
  <div class="controls">
    <header>
      <h1>Jon Tracker</h1>
      <div class="header-actions">
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
      bind:focusedDate
      onToggle={toggle}
      onCreate={(dateYMD) =>
        openCreate({ task_type: 'fixed', fixed_date: dateYMD, start: '', end: '' })}
    />
  {:else}
    <WeekGrid
      {tasks}
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
</style>
