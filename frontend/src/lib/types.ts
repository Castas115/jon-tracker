export type TaskType = 'recurring' | 'single' | 'birthday' | 'weekly_goal';

export type Task = {
  id: number;
  title: string;
  task_type: TaskType;
  weekdays: number[] | null; // recurring: 1+ entries; otherwise null
  fixed_date: string | null; // YYYY-MM-DD; single & birthday
  start_time: string | null; // "HH:MM" or null = all day
  end_time: string | null;
  is_todo: boolean; // when true, shows a checkbox and tracks completion. Always false for birthday, always true for weekly_goal
  target_per_week: number | null; // weekly_goal: completions per ISO week to hit the target
  created_at: string;
  completed_dates: string[];
};

export const WEEKDAY_LABELS = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'] as const;
export const WEEKDAY_LABELS_LONG = [
  'Monday',
  'Tuesday',
  'Wednesday',
  'Thursday',
  'Friday',
  'Saturday',
  'Sunday'
] as const;
