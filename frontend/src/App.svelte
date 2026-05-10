<script lang="ts">
  import { onMount } from 'svelte';
  import { api, weekDates, todayWeekday } from './lib/api';
  import { WEEKDAY_LABELS_LONG, type Task } from './lib/types';

  let tasks = $state<Task[]>([]);
  let loading = $state(true);
  let error = $state<string | null>(null);
  let newTitle = $state('');
  let newWeekday = $state(todayWeekday());

  const dates = weekDates();
  const today = todayWeekday();

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
      const t = await api.create(title, newWeekday);
      tasks = [...tasks, t];
      newTitle = '';
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
    if (!confirm(`¿Eliminar "${t.title}"?`)) return;
    try {
      await api.remove(t.id);
      tasks = tasks.filter((x) => x.id !== t.id);
    } catch (e) {
      error = e instanceof Error ? e.message : String(e);
    }
  }

  function tasksForDay(weekday: number): Task[] {
    return tasks.filter((t) => t.weekday === weekday);
  }

  onMount(load);
</script>

<main>
  <header>
    <h1>Jon Tracker</h1>
  </header>

  <form class="add" onsubmit={addTask}>
    <input
      bind:value={newTitle}
      placeholder="Nueva tarea..."
      maxlength="200"
      required
    />
    <select bind:value={newWeekday}>
      {#each WEEKDAY_LABELS_LONG as label, i}
        <option value={i}>{label}</option>
      {/each}
    </select>
    <button type="submit" aria-label="Añadir">+</button>
  </form>

  {#if error}
    <div class="error">{error}</div>
  {/if}

  {#if loading}
    <p class="empty">Cargando...</p>
  {:else if tasks.length === 0}
    <p class="empty">Sin tareas. Añade la primera ↑</p>
  {:else}
    {#each WEEKDAY_LABELS_LONG as label, weekday}
      {@const dayTasks = tasksForDay(weekday)}
      {#if dayTasks.length}
        <h2 class:today={weekday === today}>
          {label}{weekday === today ? ' · hoy' : ''}
        </h2>
        <article class:today={weekday === today}>
          <ul>
            {#each dayTasks as t (t.id)}
              {@const dateForDay = dates[weekday]}
              {@const done = t.completed_dates.includes(dateForDay)}
              <li class:done>
                <label>
                  <input
                    type="checkbox"
                    checked={done}
                    onchange={() => toggle(t, dateForDay)}
                  />
                  <span>{t.title}</span>
                </label>
                <button class="icon" aria-label="Eliminar" onclick={() => remove(t)}>×</button>
              </li>
            {/each}
          </ul>
        </article>
      {/if}
    {/each}
  {/if}
</main>
