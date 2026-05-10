export function isInputFocused(): boolean {
  const el = document.activeElement as HTMLElement | null;
  if (!el) return false;
  const tag = el.tagName;
  return (
    tag === 'INPUT' ||
    tag === 'TEXTAREA' ||
    tag === 'SELECT' ||
    el.isContentEditable
  );
}

/** Key matches a single character key without modifiers (Ctrl/Meta/Alt). */
export function isPlainKey(e: KeyboardEvent, key: string): boolean {
  return (
    e.key === key && !e.ctrlKey && !e.metaKey && !e.altKey
  );
}
