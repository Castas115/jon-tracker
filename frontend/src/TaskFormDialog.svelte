<script lang="ts">
  import { WEEKDAY_LABELS_LONG } from './lib/types';

  export type TaskFormValues = {
    title: string;
    weekday: number;
    start_time: string | null;
    end_time: string | null;
  };

  type Initial = Partial<{
    title: string;
    weekday: number;
    start: string;
    end: string;
  }>;

  type Props = {
    open: boolean;
    initial?: Initial;
    onSubmit: (v: TaskFormValues) => void;
    onClose: () => void;
  };

  const { open, initial = {}, onSubmit, onClose }: Props = $props();

  let dialog: HTMLDialogElement | null = $state(null);
  let titleInput: HTMLInputElement | null = $state(null);

  let title = $state('');
  let weekday = $state(0);
  let start = $state('');
  let end = $state('');
  let localError = $state<string | null>(null);

  $effect(() => {
    if (!dialog) return;
    if (open && !dialog.open) {
      title = initial.title ?? '';
      weekday = initial.weekday ?? 0;
      start = initial.start ?? '';
      end = initial.end ?? '';
      localError = null;
      dialog.showModal();
      queueMicrotask(() => titleInput?.focus());
    } else if (!open && dialog.open) {
      dialog.close();
    }
  });

  function handleSubmit(e: SubmitEvent) {
    e.preventDefault();
    const t = title.trim();
    if (!t) {
      localError = 'Title is required';
      return;
    }
    if (start && end && end <= start) {
      localError = 'End must be after start';
      return;
    }
    onSubmit({
      title: t,
      weekday,
      start_time: start || null,
      end_time: start && end ? end : null
    });
  }

  function handleBackdropClick(e: MouseEvent) {
    if (e.target === dialog) onClose();
  }
</script>

<dialog bind:this={dialog} onclose={onClose} onclick={handleBackdropClick}>
  <form onsubmit={handleSubmit}>
    <h2>New task</h2>

    <label class="field">
      <span>Title</span>
      <input
        bind:this={titleInput}
        bind:value={title}
        type="text"
        maxlength="200"
        required
        placeholder="e.g. Gym, Stand-up, Pay rent..."
      />
    </label>

    <label class="field">
      <span>Day</span>
      <select bind:value={weekday}>
        {#each WEEKDAY_LABELS_LONG as label, i}
          <option value={i}>{label}</option>
        {/each}
      </select>
    </label>

    <div class="row">
      <label class="field">
        <span>Start</span>
        <input type="time" bind:value={start} />
      </label>
      <label class="field">
        <span>End</span>
        <input type="time" bind:value={end} disabled={!start} />
      </label>
    </div>

    <p class="hint">Leave times empty to make it an all-day task.</p>

    {#if localError}
      <p class="err">{localError}</p>
    {/if}

    <div class="actions">
      <button type="button" class="ghost" onclick={onClose}>Cancel</button>
      <button type="submit" class="primary">Save</button>
    </div>
  </form>
</dialog>

<style>
  dialog {
    background: var(--bg-2);
    color: var(--fg);
    border: 1px solid var(--border);
    border-radius: var(--radius);
    padding: 1.25rem;
    width: min(440px, 92vw);
    box-shadow: 0 20px 60px rgba(0, 0, 0, 0.5);
  }
  dialog::backdrop {
    background: rgba(0, 0, 0, 0.5);
    backdrop-filter: blur(2px);
  }

  h2 {
    margin: 0 0 1rem;
    font-size: 1.05rem;
    font-weight: 600;
  }

  form {
    display: flex;
    flex-direction: column;
    gap: 0.75rem;
  }

  .field {
    display: flex;
    flex-direction: column;
    gap: 0.3rem;
    font-size: 0.78rem;
    color: var(--fg-muted);
    text-transform: uppercase;
    letter-spacing: 0.05em;
  }
  .field input,
  .field select {
    width: 100%;
    text-transform: none;
    letter-spacing: normal;
    font-size: 0.95rem;
    color: var(--fg);
  }

  .row {
    display: flex;
    gap: 0.75rem;
  }
  .row .field {
    flex: 1;
  }

  .hint {
    margin: 0;
    font-size: 0.75rem;
    color: var(--fg-muted);
  }
  .err {
    margin: 0;
    color: var(--danger);
    font-size: 0.85rem;
  }

  .actions {
    display: flex;
    justify-content: flex-end;
    gap: 0.5rem;
    margin-top: 0.5rem;
  }
  .actions button {
    padding: 0.55rem 1rem;
    border-radius: 8px;
    cursor: pointer;
    font: inherit;
    font-weight: 500;
  }
  .actions .ghost {
    background: transparent;
    border: 1px solid var(--border);
    color: var(--fg-muted);
  }
  .actions .ghost:hover {
    color: var(--fg);
    background: var(--bg-3);
  }
  .actions .primary {
    background: var(--accent);
    color: var(--accent-fg);
    border: 1px solid var(--accent);
    font-weight: 600;
  }
</style>
