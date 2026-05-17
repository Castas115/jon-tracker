<script lang="ts">
  type View = 'day' | 'week' | 'month' | 'backlog' | 'streaks' | 'inbox';

  type Props = {
    open: boolean;
    view?: View;
    onClose: () => void;
  };

  const { open, view = 'week', onClose }: Props = $props();

  let dialog: HTMLDialogElement | null = $state(null);

  $effect(() => {
    if (!dialog) return;
    if (open && !dialog.open) dialog.showModal();
    else if (!open && dialog.open) dialog.close();
  });

  function handleBackdropClick(e: MouseEvent) {
    if (e.target === dialog) onClose();
  }

  type Row = { keys: string[]; desc: string };

  const globalRows: Row[] = [
    { keys: ['n'], desc: 'New task' },
    { keys: ['⌥H'], desc: 'Cycle view back (month → week → day)' },
    { keys: ['⌥L'], desc: 'Cycle view forward (day → week → month)' },
    { keys: ['b'], desc: 'Backlog view' },
    { keys: ['t'], desc: 'Toggle theme' },
    { keys: ['s'], desc: 'Streaks view' },
    { keys: ['i'], desc: 'Inbox view' },
    { keys: ['g'], desc: 'Jump to today · press again to bounce back to where you were' },
    { keys: ['?'], desc: 'Toggle this help' },
    { keys: ['Esc'], desc: 'Close dialog / clear pending count' }
  ];

  const dayRows: Row[] = [
    { keys: ['h', '←'], desc: 'Previous day' },
    { keys: ['l', '→'], desc: 'Next day' },
    { keys: ['j', '↓'], desc: 'Forward 1 week' },
    { keys: ['k', '↑'], desc: 'Back 1 week' },
    { keys: ['⇧H'], desc: 'Previous week' },
    { keys: ['⇧L'], desc: 'Next week' }
  ];

  const weekRows: Row[] = [
    { keys: ['h', '←'], desc: 'Focus previous weekday' },
    { keys: ['l', '→'], desc: 'Focus next weekday' },
    { keys: ['j', '↓'], desc: 'Focus next hour' },
    { keys: ['k', '↑'], desc: 'Focus previous hour' },
    { keys: ['⇧H'], desc: 'Previous week' },
    { keys: ['⇧L'], desc: 'Next week' }
  ];

  const monthRows: Row[] = [
    { keys: ['h', '←'], desc: 'Previous day' },
    { keys: ['l', '→'], desc: 'Next day' },
    { keys: ['j', '↓'], desc: 'Next week row' },
    { keys: ['k', '↑'], desc: 'Previous week row' },
    { keys: ['⇧H'], desc: 'Previous month' },
    { keys: ['⇧L'], desc: 'Next month' }
  ];

  const backlogRows: Row[] = [
    {
      keys: ['—'],
      desc: 'Click the circle to mark done · pick a date to schedule · × to delete'
    }
  ];

  const streaksRows: Row[] = [
    { keys: ['—'], desc: 'Goal heatmap — hover a cell for the date + count' }
  ];

  const inboxRows: Row[] = [
    { keys: ['—'], desc: 'Captured ideas — tap an item to chat with the AI' }
  ];

  const viewRows = $derived(
    view === 'day'
      ? dayRows
      : view === 'week'
        ? weekRows
        : view === 'month'
          ? monthRows
          : view === 'streaks'
            ? streaksRows
            : view === 'inbox'
              ? inboxRows
              : backlogRows
  );

  const viewTitle = $derived(
    view === 'day'
      ? 'Day view'
      : view === 'week'
        ? 'Week view'
        : view === 'month'
          ? 'Month view'
          : view === 'streaks'
            ? 'Streaks'
            : view === 'inbox'
              ? 'Inbox'
              : 'Backlog'
  );

  const allRows = $derived([
    { section: 'Global', rows: globalRows },
    { section: viewTitle, rows: viewRows },
    {
      section: 'Counts',
      rows: [
        { keys: ['<n>'], desc: 'Prefix a motion with a number (e.g. 3j) to repeat' }
      ] as Row[]
    }
  ]);
</script>

<dialog bind:this={dialog} onclose={onClose} onclick={handleBackdropClick}>
  <h2>Keyboard shortcuts</h2>
  {#each allRows as group}
    <h3>{group.section}</h3>
    <table>
      <tbody>
        {#each group.rows as s}
          <tr>
            <td class="keys">
              {#each s.keys as k, i}
                {#if i > 0}<span class="sep">/</span>{/if}
                <kbd>{k}</kbd>
              {/each}
            </td>
            <td>{s.desc}</td>
          </tr>
        {/each}
      </tbody>
    </table>
  {/each}
  <div class="actions">
    <button type="button" class="primary" onclick={onClose}>Close</button>
  </div>
</dialog>

<style>
  dialog {
    background: var(--bg-2);
    color: var(--fg);
    border: 1px solid var(--border);
    border-radius: var(--radius);
    padding: 1.25rem;
    width: min(460px, 92vw);
    max-height: 88vh;
    overflow-y: auto;
    box-shadow: 0 20px 60px rgba(0, 0, 0, 0.5);
  }
  dialog::backdrop {
    background: rgba(0, 0, 0, 0.6);
  }
  h2 {
    margin: 0 0 0.85rem;
    font-size: 1.05rem;
    font-weight: 600;
  }
  h3 {
    margin: 0.85rem 0 0.3rem;
    font-size: 0.72rem;
    font-weight: 600;
    text-transform: uppercase;
    letter-spacing: 0.08em;
    color: var(--fg-muted);
  }
  table {
    width: 100%;
    border-collapse: collapse;
    font-size: 0.85rem;
  }
  tr {
    border-bottom: 1px solid var(--border);
  }
  tr:last-child {
    border-bottom: none;
  }
  td {
    padding: 0.4rem 0.5rem;
    vertical-align: middle;
  }
  td.keys {
    width: 38%;
    white-space: nowrap;
  }
  kbd {
    background: var(--bg-3);
    border: 1px solid var(--border);
    border-radius: 4px;
    padding: 1px 6px;
    font-family: ui-monospace, monospace;
    font-size: 0.78rem;
    color: var(--fg);
  }
  .sep {
    color: var(--fg-muted);
    margin: 0 4px;
  }
  .actions {
    display: flex;
    justify-content: flex-end;
    margin-top: 0.85rem;
  }
  .actions .primary {
    background: var(--accent);
    color: var(--accent-fg);
    border: 1px solid var(--accent);
    padding: 0.5rem 1rem;
    border-radius: 8px;
    cursor: pointer;
    font-weight: 600;
  }
</style>
