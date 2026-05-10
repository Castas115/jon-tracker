<script lang="ts">
  type Props = {
    open: boolean;
    onClose: () => void;
  };

  const { open, onClose }: Props = $props();

  let dialog: HTMLDialogElement | null = $state(null);

  $effect(() => {
    if (!dialog) return;
    if (open && !dialog.open) dialog.showModal();
    else if (!open && dialog.open) dialog.close();
  });

  function handleBackdropClick(e: MouseEvent) {
    if (e.target === dialog) onClose();
  }

  const shortcuts = [
    { keys: ['n'], desc: 'New task' },
    { keys: ['v'], desc: 'Day view' },
    { keys: ['c'], desc: 'Week view' },
    { keys: ['x'], desc: 'Month view' },
    { keys: ['t'], desc: 'Toggle theme' },
    { keys: ['h', '←'], desc: 'Previous day / month' },
    { keys: ['l', '→'], desc: 'Next day / month' },
    { keys: ['j', '↓'], desc: 'Next hour / week row' },
    { keys: ['k', '↑'], desc: 'Previous hour / week row' },
    { keys: ['Space'], desc: 'Toggle completion on focused task' },
    { keys: ['T'], desc: 'Jump to today' },
    { keys: ['?'], desc: 'Show this help' },
    { keys: ['Esc'], desc: 'Close dialog / clear selection' }
  ];
</script>

<dialog bind:this={dialog} onclose={onClose} onclick={handleBackdropClick}>
  <h2>Keyboard shortcuts</h2>
  <table>
    <tbody>
      {#each shortcuts as s}
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
    width: min(420px, 92vw);
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
    padding: 0.45rem 0.5rem;
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
