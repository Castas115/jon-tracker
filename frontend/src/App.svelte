<script lang="ts">
  import { onMount } from 'svelte';
  import { api, todayWeekday } from './lib/api';
  import { type Task } from './lib/types';
  import { applyTheme, loadTheme, saveTheme, type Theme } from './lib/theme';
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
      task_type: 'recurring',
      weekdays: [todayWeekday()],
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

  onMount(() => {
    theme = loadTheme();
    applyTheme(theme);
    load();
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
        >
          + New task
        </button>
        <button
          class="icon"
          type="button"
          aria-label={theme === 'dark' ? 'Day mode' : 'Night mode'}
          title={theme === 'dark' ? 'Day mode' : 'Night mode'}
          onclick={toggleTheme}
        >
          {theme === 'dark' ? '☀' : '☾'}
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
      onToggle={toggle}
      onCreate={(dateYMD) =>
        openCreate({ task_type: 'fixed', fixed_date: dateYMD, start: '', end: '' })}
    />
  {:else}
    <WeekGrid
      {tasks}
      onToggle={toggle}
      onRemove={remove}
      onCreate={(weekday, dateYMD, start, end) =>
        openCreate({
          task_type: 'recurring',
          weekdays: [weekday],
          fixed_date: dateYMD,
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
