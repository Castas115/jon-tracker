import { isoWeekNumber, mondayOf, weekdayMonFirst, ymd } from './dates';
import type { Task, TargetSegment } from './types';

export type SegmentProgress = {
  segment: TargetSegment;
  done: number;
  hit: boolean;
  exceeded: boolean;
};

export type GoalStatus = {
  done: number;
  target: number; // total target (sum of segments when present, else target_per_week)
  hit: boolean; // every segment (or flat goal) reached its target
  exceeded: boolean; // any segment exceeded, or flat done > target
  segments: SegmentProgress[]; // empty when the task is a flat target_per_week goal
};

function inSameIsoWeek(a: Date, b: Date): boolean {
  return a.getFullYear() === b.getFullYear() && isoWeekNumber(a) === isoWeekNumber(b);
}

/** Count completions in the ISO week containing [refDate], optionally filtered by weekdays. */
function countInWeek(t: Task, refDate: Date, weekdays?: number[]): number {
  return t.completed_dates.reduce((acc, cd) => {
    const d = new Date(cd + 'T00:00:00');
    if (!inSameIsoWeek(d, refDate)) return acc;
    if (weekdays && !weekdays.includes(weekdayMonFirst(d))) return acc;
    return acc + 1;
  }, 0);
}

export function goalStatus(t: Task, refDateYMD: string): GoalStatus {
  const ref = new Date(refDateYMD + 'T00:00:00');
  const segments = t.target_segments ?? [];
  if (segments.length === 0) {
    const target = t.target_per_week ?? 0;
    const done = countInWeek(t, ref);
    return {
      done,
      target,
      hit: target > 0 && done >= target,
      exceeded: target > 0 && done > target,
      segments: [],
    };
  }
  const perSegment: SegmentProgress[] = segments.map((seg) => {
    const done = countInWeek(t, ref, seg.weekdays);
    return {
      segment: seg,
      done,
      hit: done >= seg.target,
      exceeded: done > seg.target,
    };
  });
  const done = perSegment.reduce((a, s) => a + s.done, 0);
  const target = segments.reduce((a, s) => a + s.target, 0);
  return {
    done,
    target,
    hit: perSegment.every((p) => p.hit),
    exceeded: perSegment.some((p) => p.exceeded),
    segments: perSegment,
  };
}

/**
 * Cumulative count of completions in the segment that owns [dateYMD]'s
 * weekday, counting every completion in the ISO week with date <= dateYMD.
 * Used to show "(N/T)" in the row title. Handles duplicate completions
 * (same date twice) correctly because they're counted separately.
 */
export function weeklyGoalRank(t: Task, dateYMD: string): number {
  const d = new Date(dateYMD + 'T00:00:00');
  const wd = weekdayMonFirst(d);
  const segs = t.target_segments ?? [];
  const seg = segs.find((s) => s.weekdays.includes(wd));
  const filter = seg ? seg.weekdays : undefined;
  return t.completed_dates.reduce((acc, cd) => {
    if (cd > dateYMD) return acc;
    const cdd = new Date(cd + 'T00:00:00');
    if (!inSameIsoWeek(cdd, d)) return acc;
    if (filter && !filter.includes(weekdayMonFirst(cdd))) return acc;
    return acc + 1;
  }, 0);
}

export function weeklyGoalLabel(t: Task, dateYMD: string): string {
  const d = new Date(dateYMD + 'T00:00:00');
  const wd = weekdayMonFirst(d);
  const segs = t.target_segments ?? [];
  const seg = segs.find((s) => s.weekdays.includes(wd));
  const k = weeklyGoalRank(t, dateYMD);
  const tgt = seg?.target ?? t.target_per_week ?? '?';
  return `${t.title} (${k}/${tgt})`;
}

export type StreakInfo = {
  current: number; // weeks running up to (and including) the current week if hit
  best: number; // longest historical run
};

/**
 * Consecutive ISO weeks where the goal hit its target. The current week
 * counts only if it is already hit; otherwise the streak starts from the
 * previous week. Weeks before the task was created are not counted.
 */
export function streakInfo(t: Task, todayYMD: string): StreakInfo {
  const today = new Date(todayYMD + 'T00:00:00');
  const startSource = t.start_date ? new Date(t.start_date + 'T00:00:00') : new Date(t.created_at);
  const startMonday = mondayOf(startSource);
  // Cap scan at end_date when set; otherwise today.
  const endSource = t.end_date ? new Date(t.end_date + 'T00:00:00') : today;
  const endMonday = mondayOf(endSource < today ? endSource : today);
  let cursor = endMonday;

  const hit = (d: Date) => goalStatus(t, ymd(d)).hit;

  let current = 0;
  if (!hit(cursor)) {
    cursor.setDate(cursor.getDate() - 7);
  }
  while (cursor >= startMonday && hit(cursor)) {
    current++;
    cursor.setDate(cursor.getDate() - 7);
  }

  // Best streak: scan every week in the active range.
  let best = 0;
  let run = 0;
  let scan = new Date(startMonday);
  while (scan <= endMonday) {
    if (hit(scan)) {
      run++;
      if (run > best) best = run;
    } else {
      run = 0;
    }
    scan.setDate(scan.getDate() + 7);
  }
  return { current, best };
}

export const WEEKDAY_SHORT = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'] as const;

export function segmentLabel(seg: TargetSegment): string {
  // Compact label e.g. "Mon-Thu" when contiguous, else "Mon · Wed · Fri".
  const sorted = [...seg.weekdays].sort((a, b) => a - b);
  if (sorted.length === 0) return '';
  if (sorted.length === 1) return WEEKDAY_SHORT[sorted[0]];
  const contiguous = sorted.every((v, i) => i === 0 || v === sorted[i - 1] + 1);
  if (contiguous) return `${WEEKDAY_SHORT[sorted[0]]}-${WEEKDAY_SHORT[sorted[sorted.length - 1]]}`;
  return sorted.map((w) => WEEKDAY_SHORT[w]).join(' · ');
}
