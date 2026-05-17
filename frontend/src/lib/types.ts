export type TaskType = 'recurring' | 'single' | 'birthday' | 'weekly_goal';

export type TargetSegment = {
  weekdays: number[]; // 0=Mon … 6=Sun, at least one
  target: number; // completions needed within this weekday bucket per ISO week
};

export type Task = {
  id: number;
  title: string;
  description: string | null;
  task_type: TaskType;
  weekdays: number[] | null; // recurring: 1+ entries; otherwise null
  fixed_date: string | null; // YYYY-MM-DD; single & birthday
  start_time: string | null; // "HH:MM" or null = all day
  end_time: string | null;
  is_todo: boolean; // when true, shows a checkbox and tracks completion. Always false for birthday, always true for weekly_goal
  target_per_week: number | null; // weekly_goal: flat target. Ignored when target_segments is set.
  target_segments: TargetSegment[] | null; // weekly_goal: weekday-bucketed targets
  show_in_upcoming: boolean; // false → hide from the day-view "Upcoming" panel
  start_date: string | null; // YYYY-MM-DD. Recurring/weekly_goal don't apply before this date.
  end_date: string | null; // YYYY-MM-DD. Recurring/weekly_goal stop applying after this date.
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
