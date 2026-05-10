export type Theme = 'dark' | 'light';

const KEY = 'tracker-theme';

export function loadTheme(): Theme {
  const saved = localStorage.getItem(KEY) as Theme | null;
  if (saved === 'dark' || saved === 'light') return saved;
  // system preference fallback
  if (window.matchMedia?.('(prefers-color-scheme: light)').matches) return 'light';
  return 'dark';
}

export function applyTheme(theme: Theme): void {
  document.documentElement.dataset.theme = theme;
  document
    .querySelector('meta[name="theme-color"]')
    ?.setAttribute('content', theme === 'dark' ? '#000000' : '#ffffff');
}

export function saveTheme(theme: Theme): void {
  localStorage.setItem(KEY, theme);
  applyTheme(theme);
}
