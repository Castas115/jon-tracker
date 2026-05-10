export type Task = {
  id: number;
  title: string;
  weekday: number;
  start_time: string | null; // "HH:MM" or null = todo el día
  end_time: string | null;
  created_at: string;
  completed_dates: string[];
};

export const WEEKDAY_LABELS = ['Lun', 'Mar', 'Mié', 'Jue', 'Vie', 'Sáb', 'Dom'] as const;
export const WEEKDAY_LABELS_LONG = [
  'Lunes',
  'Martes',
  'Miércoles',
  'Jueves',
  'Viernes',
  'Sábado',
  'Domingo'
] as const;
