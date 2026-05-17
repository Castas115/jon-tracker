<script lang="ts">
  import { mondayOf, ymd } from './lib/dates';
  import { goalStatus, segmentLabel, streakInfo } from './lib/weeklyGoal';
  import type { Task } from './lib/types';

  type Props = {
    tasks: Task[];
  };

  const { tasks }: Props = $props();

  const WEEKS = 18; // horizon shown in the heatmap, including current week
  const WEEKDAY_SHORT = ['M', 'T', 'W', 'T', 'F', 'S', 'S'];

  const today = $derived(ymd(new Date()));
  const todayDate = $derived(new Date(today + 'T00:00:00'));

  type Cell = {
    date: Date;
    ymd: string;
    inActive: boolean; // within task active range
    inSegment: boolean; // weekday counts toward target
    count: number; // number of completions on this date
    isToday: boolean;
    isFuture: boolean;
  };

  type Heatmap = {
    weeks: Date[]; // monday of each week shown, oldest→newest
    cells: Cell[][]; // [weekday][weekIdx]
    monthLabels: { idx: number; label: string }[];
  };

  type Card = {
    task: Task;
    current: number;
    best: number;
    weekDone: number;
    weekTarget: number;
    weekHit: boolean;
    weekExceeded: boolean;
    segLabels: string;
    map: Heatmap;
  };

  function buildHeatmap(t: Task): Heatmap {
    const lastMonday = mondayOf(todayDate);
    const weeks: Date[] = [];
    for (let i = WEEKS - 1; i >= 0; i--) {
      const d = new Date(lastMonday);
      d.setDate(lastMonday.getDate() - i * 7);
      weeks.push(d);
    }

    const counts = new Map<string, number>();
    for (const c of t.completed_dates) counts.set(c, (counts.get(c) ?? 0) + 1);

    const segWeekdays = new Set<number>(
      (t.target_segments ?? []).flatMap((s) => s.weekdays)
    );
    const allFlatTarget = (t.target_segments ?? []).length === 0;

    const cells: Cell[][] = Array.from({ length: 7 }, () => []);
    for (const monday of weeks) {
      for (let wd = 0; wd < 7; wd++) {
        const d = new Date(monday);
        d.setDate(monday.getDate() + wd);
        const k = ymd(d);
        const inActive =
          (!t.start_date || k >= t.start_date) && (!t.end_date || k <= t.end_date);
        const inSegment = allFlatTarget || segWeekdays.has(wd);
        cells[wd].push({
          date: d,
          ymd: k,
          inActive,
          inSegment,
          count: counts.get(k) ?? 0,
          isToday: k === today,
          isFuture: k > today
        });
      }
    }

    const monthLabels: { idx: number; label: string }[] = [];
    let lastMonth = -1;
    for (let i = 0; i < weeks.length; i++) {
      const m = weeks[i].getMonth();
      if (m !== lastMonth) {
        monthLabels.push({
          idx: i,
          label: weeks[i].toLocaleString(undefined, { month: 'short' })
        });
        lastMonth = m;
      }
    }
    return { weeks, cells, monthLabels };
  }

  const cards = $derived.by<Card[]>(() => {
    return tasks
      .filter((t) => t.task_type === 'weekly_goal')
      .map((t) => {
        const s = streakInfo(t, today);
        const g = goalStatus(t, today);
        const segs = t.target_segments ?? [];
        const segLabels =
          segs.length === 0
            ? `${t.target_per_week ?? 0}/wk`
            : segs.map((seg) => `${seg.target}x ${segmentLabel(seg)}`).join(' + ');
        return {
          task: t,
          current: s.current,
          best: s.best,
          weekDone: g.done,
          weekTarget: g.target,
          weekHit: g.hit,
          weekExceeded: g.exceeded,
          segLabels,
          map: buildHeatmap(t)
        };
      })
      .sort(
        (a, b) =>
          b.current - a.current || b.best - a.best || a.task.title.localeCompare(b.task.title)
      );
  });

  function cellTitle(c: Cell): string {
    const base = `${c.ymd} — ${c.count} completion${c.count === 1 ? '' : 's'}`;
    if (!c.inActive) return `${base} (outside active range)`;
    if (!c.inSegment) return `${base} (not in any segment)`;
    return base;
  }

  function flame(n: number): string {
    if (n === 0) return '·';
    if (n >= 12) return '🔥🔥🔥';
    if (n >= 6) return '🔥🔥';
    return '🔥';
  }
</script>

<section class="streaks">
  {#if cards.length === 0}
    <p class="empty">No weekly goals yet.</p>
  {:else}
    <div class="cards">
      {#each cards as c (c.task.id)}
        <article class="card">
          <header>
            <div class="title-row">
              <span class="title">{c.task.title}</span>
              <span class="sub">{c.segLabels}</span>
            </div>
            <div class="metrics">
              <span class="metric" title="Current streak">
                <span class="flame">{flame(c.current)}</span>
                <span class="num">{c.current}</span>
                <span class="lbl">cur</span>
              </span>
              <span class="metric" title="Best streak">
                <span class="num">{c.best}</span>
                <span class="lbl">best</span>
              </span>
              <span class="metric" title="This week" class:hit={c.weekHit}>
                <span class="num">{c.weekDone}/{c.weekTarget}</span>
                <span class="lbl">wk</span>
              </span>
            </div>
          </header>

          <div class="heatmap">
            <div class="months" style="grid-template-columns: repeat({WEEKS}, var(--cell));">
              {#each c.map.monthLabels as ml}
                <span class="month" style="grid-column-start: {ml.idx + 1};">{ml.label}</span>
              {/each}
            </div>
            <div class="grid">
              <div class="weekday-col">
                {#each WEEKDAY_SHORT as wd, i}
                  <span class="weekday" class:dim={i % 2 === 1}>{wd}</span>
                {/each}
              </div>
              <div class="cells" style="grid-template-columns: repeat({WEEKS}, var(--cell));">
                {#each c.map.cells as row, wd}
                  {#each row as cell, wi}
                    <span
                      class="cell"
                      class:outside={!cell.inActive || cell.isFuture}
                      class:off-seg={cell.inActive && !cell.isFuture && !cell.inSegment}
                      class:today={cell.isToday}
                      data-count={Math.min(cell.count, 4)}
                      title={cellTitle(cell)}
                      style="grid-row: {wd + 1}; grid-column: {wi + 1};"
                    ></span>
                  {/each}
                {/each}
              </div>
            </div>
          </div>
        </article>
      {/each}
    </div>
  {/if}
</section>

<style>
  .streaks {
    display: flex;
    flex-direction: column;
    flex: 1;
    min-height: 0;
    overflow-y: auto;
    padding: 0.25rem;
  }
  .empty {
    color: var(--fg-muted);
    font-size: 0.9rem;
  }

  .cards {
    display: flex;
    flex-direction: column;
    gap: 1rem;
  }
  .card {
    --cell: 14px;
    background: var(--bg);
    border: 1px solid var(--border);
    border-radius: 8px;
    padding: 0.75rem;
  }
  .card header {
    display: flex;
    flex-direction: column;
    gap: 0.35rem;
    margin-bottom: 0.6rem;
  }
  .title-row {
    display: flex;
    align-items: baseline;
    gap: 0.5rem;
    flex-wrap: wrap;
  }
  .title { font-weight: 600; font-size: 0.95rem; }
  .sub { font-size: 0.72rem; color: var(--fg-muted); }

  .metrics {
    display: flex;
    gap: 0.85rem;
    align-items: center;
    font-size: 0.78rem;
  }
  .metric { display: inline-flex; align-items: baseline; gap: 0.3rem; color: var(--fg); }
  .metric .lbl { color: var(--fg-muted); font-size: 0.66rem; text-transform: uppercase; letter-spacing: 0.05em; }
  .metric.hit .num { color: var(--accent); font-weight: 600; }
  .flame { margin-right: 2px; }
  .num { font-variant-numeric: tabular-nums; font-weight: 600; }

  .heatmap {
    display: flex;
    flex-direction: column;
    gap: 4px;
    overflow-x: auto;
  }
  .months {
    display: grid;
    margin-left: 22px; /* weekday-col width */
    column-gap: 2px;
    font-size: 0.62rem;
    color: var(--fg-muted);
    text-transform: uppercase;
    letter-spacing: 0.06em;
    height: 1em;
  }
  .month {
    grid-row: 1;
    white-space: nowrap;
  }

  .grid {
    display: flex;
    gap: 4px;
  }
  .weekday-col {
    display: grid;
    grid-template-rows: repeat(7, var(--cell));
    row-gap: 2px;
    width: 18px;
    align-items: center;
  }
  .weekday {
    font-size: 0.6rem;
    color: var(--fg-muted);
    line-height: var(--cell);
  }
  .weekday.dim { opacity: 0.55; }

  .cells {
    display: grid;
    grid-template-rows: repeat(7, var(--cell));
    column-gap: 2px;
    row-gap: 2px;
  }
  .cell {
    width: var(--cell);
    height: var(--cell);
    background: var(--bg-3);
    border-radius: 3px;
    border: 1px solid transparent;
  }
  .cell.off-seg {
    background: color-mix(in srgb, var(--bg-3) 60%, transparent);
    opacity: 0.5;
  }
  .cell.outside {
    background: transparent;
    border-color: var(--bg-3);
  }
  .cell.today {
    border-color: var(--today, var(--accent));
  }
  .cell[data-count='1'] { background: color-mix(in srgb, var(--accent) 35%, var(--bg-3)); }
  .cell[data-count='2'] { background: color-mix(in srgb, var(--accent) 60%, var(--bg-3)); }
  .cell[data-count='3'] { background: color-mix(in srgb, var(--accent) 80%, var(--bg-3)); }
  .cell[data-count='4'] { background: var(--accent); }
</style>
