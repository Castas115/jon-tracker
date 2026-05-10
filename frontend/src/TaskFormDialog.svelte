<script lang="ts">
  import { WEEKDAY_LABELS, WEEKDAY_LABELS_LONG, type TaskType } from './lib/types';

  export type TaskFormValues = {
    title: string;
    task_type: TaskType;
    weekdays: number[] | null;
    fixed_date: string | null;
    start_time: string | null;
    end_time: string | null;
  };

  type Initial = Partial<{
    title: string;
    task_type: TaskType;
    weekdays: number[];
    fixed_date: string;
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
  let taskType = $state<TaskType>('recurring');
  let weekdays = $state<number[]>([]);
  let fixedDate = $state('');
  let start = $state('');
  let end = $state('');
  let localError = $state<string | null>(null);

  // Track only `open` so typing in the form doesn't re-run this effect.
  // The `initial` snapshot is captured untracked at the open transition.
  $effect(() => {
    if (!dialog) return;
    if (open && !dialog.open) {
      const snap = $state.snapshot(initial);
      title = snap.title ?? '';
      taskType = snap.task_type ?? 'recurring';
      weekdays = snap.weekdays ?? [];
      fixedDate = snap.fixed_date ?? '';
      start = snap.start ?? '';
      end = snap.end ?? '';
      localError = null;
      dialog.showModal();
      queueMicrotask(() => titleInput?.focus());
    } else if (!open && dialog.open) {
      dialog.close();
    }
  });

  function toggleDay(d: number) {
    weekdays = weekdays.includes(d)
      ? weekdays.filter((x) => x !== d)
      : [...weekdays, d].sort((a, b) => a - b);
  }

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
    if (taskType === 'recurring' && weekdays.length === 0) {
      localError = 'Pick at least one day';
      return;
    }
    if ((taskType === 'fixed' || taskType === 'birthday') && !fixedDate) {
      localError = 'Pick a date';
      return;
    }

    const isBirthday = taskType === 'birthday';
    onSubmit({
      title: t,
      task_type: taskType,
      weekdays: taskType === 'recurring' ? weekdays : null,
      fixed_date: taskType === 'fixed' || taskType === 'birthday' ? fixedDate : null,
      start_time: isBirthday ? null : (start || null),
      end_time: isBirthday ? null : (start && end ? end : null)
    });
  }

  function handleBackdropClick(e: MouseEvent) {
    if (e.target === dialog) onClose();
  }
</script>

<dialog bind:this={dialog} onclose={onClose} onclick={handleBackdropClick}>
  <form onsubmit={handleSubmit}>
    <h2>New task</h2>

    <div class="type-tabs" role="tablist">
      <button
        type="button"
        class="type-tab"
        class:active={taskType === 'fixed'}
        role="tab"
        aria-selected={taskType === 'fixed'}
        onclick={() => (taskType = 'fixed')}
      >
        Fixed
      </button>
      <button
        type="button"
        class="type-tab"
        class:active={taskType === 'recurring'}
        role="tab"
        aria-selected={taskType === 'recurring'}
        onclick={() => (taskType = 'recurring')}
      >
        Recurring
      </button>
      <button
        type="button"
        class="type-tab"
        class:active={taskType === 'birthday'}
        role="tab"
        aria-selected={taskType === 'birthday'}
        onclick={() => (taskType = 'birthday')}
      >
        🎂 Birthday
      </button>
    </div>

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

    {#if taskType === 'recurring'}
      <div class="field">
        <span>Days</span>
        <div class="days">
          {#each WEEKDAY_LABELS as label, i}
            <button
              type="button"
              class="day"
              class:on={weekdays.includes(i)}
              aria-label={WEEKDAY_LABELS_LONG[i]}
              aria-pressed={weekdays.includes(i)}
              onclick={() => toggleDay(i)}
            >
              {label}
            </button>
          {/each}
        </div>
      </div>
    {:else}
      <label class="field">
        <span>{taskType === 'birthday' ? 'Birth date' : 'Date'}</span>
        <input type="date" bind:value={fixedDate} required />
      </label>
    {/if}

    {#if taskType !== 'birthday'}
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

      <p class="hint">Leave times empty for an all-day task.</p>
    {:else}
      <p class="hint">Birthdays repeat every year on the same month and day.</p>
    {/if}

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
    background: rgba(0, 0, 0, 0.6);
  }

  h2 {
    margin: 0 0 0.85rem;
    font-size: 1.05rem;
    font-weight: 600;
  }

  form {
    display: flex;
    flex-direction: column;
    gap: 0.75rem;
  }

  .type-tabs {
    display: flex;
    gap: 4px;
    background: var(--bg-3);
    padding: 4px;
    border-radius: 8px;
    border: 1px solid var(--border);
  }
  .type-tab {
    flex: 1;
    background: transparent;
    border: none;
    color: var(--fg-muted);
    padding: 0.45rem 0.75rem;
    border-radius: 6px;
    cursor: pointer;
    font: inherit;
    font-size: 0.85rem;
    transition: background-color 100ms ease, color 100ms ease;
  }
  .type-tab:hover { color: var(--fg); }
  .type-tab.active {
    background: var(--accent);
    color: var(--accent-fg);
    font-weight: 600;
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
  .field input {
    width: 100%;
    text-transform: none;
    letter-spacing: normal;
    font-size: 0.95rem;
    color: var(--fg);
  }

  .days {
    display: grid;
    grid-template-columns: repeat(7, 1fr);
    gap: 4px;
  }
  .day {
    background: var(--bg-3);
    border: 1px solid var(--border);
    color: var(--fg-muted);
    padding: 0.5rem 0;
    border-radius: 6px;
    cursor: pointer;
    font: inherit;
    font-size: 0.8rem;
    text-transform: none;
    letter-spacing: normal;
  }
  .day:hover { color: var(--fg); }
  .day.on {
    background: var(--accent);
    color: var(--accent-fg);
    border-color: var(--accent);
    font-weight: 600;
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
