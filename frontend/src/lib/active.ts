import type { Task } from './types';

/**
 * Whether [t] is active on [dateYMD]. Returns true unless the task is a
 * recurring/weekly_goal that started after dateYMD or ended before it.
 * Other task types ignore the start_date/end_date pair because their
 * scheduling is anchored on fixed_date.
 */
export function isActiveOn(t: Task, dateYMD: string): boolean {
  if (t.task_type !== 'recurring' && t.task_type !== 'weekly_goal') return true;
  if (t.start_date && dateYMD < t.start_date) return false;
  if (t.end_date && dateYMD > t.end_date) return false;
  return true;
}
