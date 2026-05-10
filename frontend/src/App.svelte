<script lang="ts">
  import { onMount } from 'svelte';
  import { api, todayWeekday } from './lib/api';
  import { WEEKDAY_LABELS_LONG, type Task } from './lib/types';
  import { applyTheme, loadTheme, saveTheme, type Theme } from './lib/theme';
  import MonthView from './MonthView.svelte';
  import WeekGrid from './WeekGrid.svelte';

  type View = 'week' | 'month';

  let tasks = $state<Task[]>([]);
  let loading = $state(true);
  let error = $state<string | null>(null);
  let newTitle = $state('');
  let newWeekday = $state(todayWeekday());
  let newStart = $state('');
  let newEnd = $state('');
  let theme = $state<Theme>('dark');
  let view = $state<View>('week');

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

  async function addTask(e: SubmitEvent) {
    e.preventDefault();
    const title = newTitle.trim();
    if (!title) return;
    try {
      const t = await api.create({
        title,
        weekday: newWeekday,
        start_time: newStart || null,
        end_time: newStart && newEnd ? newEnd : null
      });
      tasks = [...tasks, t];
      newTitle = '';
      newStart = '';
      newEnd = '';
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
  <header>
    <h1>Jon Tracker</h1>
    <button
      class="icon"
      type="button"
      aria-label={theme === 'dark' ? 'Day mode' : 'Night mode'}
      title={theme === 'dark' ? 'Day mode' : 'Night mode'}
      onclick={toggleTheme}
    >
      {theme === 'dark' ? '☀' : '☾'}
    </button>
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

  <form class="add" onsubmit={addTask}>
    <input
      class="title"
      bind:value={newTitle}
      placeholder="New task..."
      maxlength="200"
      required
    />
    <select bind:value={newWeekday}>
      {#each WEEKDAY_LABELS_LONG as label, i}
        <option value={i}>{label}</option>
      {/each}
    </select>
    <input
      class="time"
      type="time"
      bind:value={newStart}
      title="Start (empty = all day)"
    />
    <input
      class="time"
      type="time"
      bind:value={newEnd}
      title="End"
      disabled={!newStart}
    />
    <button class="primary" type="submit" aria-label="Add">+</button>
  </form>

  {#if error}
    <div class="error">{error}</div>
  {/if}

  {#if loading}
    <p class="empty">Loading...</p>
  {:else if view === 'month'}
    <MonthView {tasks} onToggle={toggle} />
  {:else}
    <WeekGrid {tasks} onToggle={toggle} onRemove={remove} />
  {/if}
</main>

<style>
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

  form.add { flex-wrap: wrap; }
  form.add .title { flex: 1 1 100%; }
  form.add select { flex: 1 1 auto; min-width: 130px; }
  form.add .time { flex: 0 0 auto; width: 110px; }
  form.add .primary { flex: 0 0 auto; }
</style>
