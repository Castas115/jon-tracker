import type { Task } from './types';

async function http<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(path, {
    headers: { 'content-type': 'application/json' },
    ...init
  });
  if (!res.ok) {
    const body = await res.text();
    throw new Error(`${res.status} ${res.statusText}: ${body}`);
  }
  if (res.status === 204) return undefined as T;
  return res.json();
}

export type CreatePayload = {
  title: string;
  task_type: 'recurring' | 'single' | 'birthday' | 'weekly_goal';
  weekdays?: number[] | null;
  fixed_date?: string | null; // YYYY-MM-DD
  start_time?: string | null;
  end_time?: string | null;
  is_todo?: boolean;
  target_per_week?: number | null;
  target_segments?: { weekdays: number[]; target: number }[] | null;
  show_in_upcoming?: boolean;
  start_date?: string | null;
  end_date?: string | null;
};

export type CalendarEvent = {
  id: string;
  title: string;
  start: string;
  end: string;
  all_day: boolean;
  kind: 'event' | 'birthday';
  location: string | null;
  description: string | null;
};

export type UpdatePayload = Partial<CreatePayload>;

export const api = {
  list: () => http<Task[]>('/tasks'),
  create: (payload: CreatePayload) =>
    http<Task>('/tasks', {
      method: 'POST',
      body: JSON.stringify(payload)
    }),
  update: (id: number, payload: UpdatePayload) =>
    http<Task>(`/tasks/${id}`, {
      method: 'PATCH',
      body: JSON.stringify(payload)
    }),
  remove: (id: number) =>
    http<void>(`/tasks/${id}`, { method: 'DELETE' }),
  toggle: (id: number, completed_on: string, action: 'toggle' | 'add' | 'remove' = 'toggle') =>
    http<Task>(`/tasks/${id}/toggle`, {
      method: 'POST',
      body: JSON.stringify({ completed_on, action })
    }),

  calendarStatus: () => http<{ configured: boolean }>('/calendar/status'),
  events: (from: string, to: string) =>
    http<CalendarEvent[]>(`/calendar/events?from=${from}&to=${to}`),

  listIdeas: () => http<Idea[]>('/ideas'),
  createIdea: (payload: { transcript: string; kind?: string; title?: string }) =>
    http<Idea>('/ideas', { method: 'POST', body: JSON.stringify(payload) }),
  updateIdea: (id: number, payload: Partial<{ kind: string; title: string; status: string; linked_task_id: number | null }>) =>
    http<Idea>(`/ideas/${id}`, { method: 'PATCH', body: JSON.stringify(payload) }),
  deleteIdea: (id: number) => http<void>(`/ideas/${id}`, { method: 'DELETE' }),
  postIdeaMessage: (id: number, payload: { role: 'user' | 'assistant'; text: string }) =>
    http<Idea>(`/ideas/${id}/messages`, { method: 'POST', body: JSON.stringify(payload) })
};

export type IdeaKind = 'task' | 'feature' | 'unknown';
export type IdeaStatus = 'new' | 'needs_info' | 'in_progress' | 'done' | 'rejected';
export type IdeaMessage = {
  id: number;
  role: 'user' | 'assistant';
  text: string;
  created_at: string;
};
export type Idea = {
  id: number;
  kind: IdeaKind;
  title: string;
  transcript: string;
  status: IdeaStatus;
  linked_task_id: number | null;
  created_at: string;
  updated_at: string;
  messages: IdeaMessage[];
};

function ymdLocal(d: Date): string {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

export function weekDates(): string[] {
  const now = new Date();
  const day = now.getDay();
  const diffToMon = day === 0 ? -6 : 1 - day;
  const mon = new Date(now);
  mon.setDate(now.getDate() + diffToMon);
  mon.setHours(0, 0, 0, 0);
  return Array.from({ length: 7 }, (_, i) => {
    const d = new Date(mon);
    d.setDate(mon.getDate() + i);
    // Use LOCAL date — toISOString() shifts to UTC and breaks Madrid (+1/+2).
    return ymdLocal(d);
  });
}

export function todayWeekday(): number {
  const day = new Date().getDay();
  return day === 0 ? 6 : day - 1;
}
