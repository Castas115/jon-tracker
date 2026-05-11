import { isoWeekNumber } from './dates';
import type { Task } from './types';

/** 1-based rank of `dateYMD` among the task's completions in the same ISO week. */
export function weeklyGoalRank(t: Task, dateYMD: string): number {
  const d = new Date(dateYMD + 'T00:00:00');
  const weekISO = isoWeekNumber(d);
  const year = d.getFullYear();
  const same = t.completed_dates
    .filter((cd) => {
      const cdd = new Date(cd + 'T00:00:00');
      return isoWeekNumber(cdd) === weekISO && cdd.getFullYear() === year;
    })
    .sort();
  return same.indexOf(dateYMD) + 1;
}

export function weeklyGoalLabel(t: Task, dateYMD: string): string {
  const k = weeklyGoalRank(t, dateYMD);
  return `${t.title} (${k}/${t.target_per_week ?? '?'})`;
}
