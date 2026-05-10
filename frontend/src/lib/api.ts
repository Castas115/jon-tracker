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

export const api = {
  list: () => http<Task[]>('/tasks'),
  create: (title: string, weekday: number) =>
    http<Task>('/tasks', {
      method: 'POST',
      body: JSON.stringify({ title, weekday })
    }),
  remove: (id: number) =>
    http<void>(`/tasks/${id}`, { method: 'DELETE' }),
  toggle: (id: number, completed_on: string) =>
    http<Task>(`/tasks/${id}/toggle`, {
      method: 'POST',
      body: JSON.stringify({ completed_on })
    })
};

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
    return d.toISOString().slice(0, 10);
  });
}

export function todayWeekday(): number {
  const day = new Date().getDay();
  return day === 0 ? 6 : day - 1;
}
