<script lang="ts">
  import { isActiveOn } from './lib/active';
  import { weekdayMonFirst, ymd } from './lib/dates';
  import { type Task } from './lib/types';
  import { weeklyGoalLabel } from './lib/weeklyGoal';

  type Props = {
    tasks: Task[];
    onToggle: (task: Task, dateYMD: string) => void;
  };

  const { tasks, onToggle }: Props = $props();

  const todayYMD = ymd(new Date());
  const todayDate = new Date(todayYMD + 'T00:00:00');
  const todayWD = weekdayMonFirst(todayDate);

  function matchesToday(t: Task): boolean {
    if (!isActiveOn(t, todayYMD)) return false;
    if (t.task_type === 'recurring') return (t.weekdays ?? []).includes(todayWD);
    if (t.task_type === 'single') return t.fixed_date === todayYMD;
    if (t.task_type === 'birthday' && t.fixed_date) {
      const bd = new Date(t.fixed_date + 'T00:00:00');
      return bd.getMonth() === todayDate.getMonth() && bd.getDate() === todayDate.getDate();
    }
    if (t.task_type === 'weekly_goal') {
      const segs = t.target_segments ?? [];
      if (segs.length === 0) return true;
      return segs.some((s) => s.weekdays.includes(todayWD));
    }
    return false;
  }

  function isDone(t: Task): boolean {
    return t.completed_dates.includes(todayYMD);
  }

  const checklist = $derived(
    tasks
      .filter((t) => matchesToday(t) && t.is_todo)
      .sort((a, b) => {
        const ta = a.start_time ?? '99:99';
        const tb = b.start_time ?? '99:99';
        return ta.localeCompare(tb) || a.title.localeCompare(b.title);
      })
  );
  const doneCount = $derived(checklist.filter(isDone).length);
  const summary = $derived(`${doneCount}/${checklist.length}`);
</script>

<aside class="sidebar">
  <article class="panel">
    <h3>Today <span class="badge">{summary}</span></h3>
    {#if checklist.length === 0}
      <p class="empty">Nothing to tick off.</p>
    {:else}
      <ul>
        {#each checklist as t (t.id)}
          {@const done = isDone(t)}
          <li class:done>
            <label>
              <input
                type="checkbox"
                checked={done}
                onchange={() => onToggle(t, todayYMD)}
              />
              <span class="t-title">{t.task_type === 'weekly_goal' ? weeklyGoalLabel(t, todayYMD) : t.title}</span>
              {#if t.start_time}<span class="t-time">{t.start_time}</span>{/if}
            </label>
          </li>
        {/each}
      </ul>
    {/if}
  </article>
</aside>

<style>
  .sidebar {
    border-left: 1px solid var(--border);
    background: var(--bg-2);
    padding: 0.75rem;
    display: flex;
    flex-direction: column;
    gap: 1rem;
    overflow-y: auto;
    min-height: 0;
    min-width: 0;
    box-sizing: border-box;
  }
  .panel {
    background: var(--bg);
    border: 1px solid var(--border);
    border-radius: 8px;
    padding: 0.6rem 0.75rem;
    margin: 0;
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
    text-align: left;
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
    padding: 0;
    border-bottom: none;
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

  @media (max-width: 960px) {
    .sidebar {
      border-left: none;
      border-top: 1px solid var(--border);
      overflow-y: visible;
    }
  }
</style>
