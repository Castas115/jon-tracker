<script lang="ts">
  import { ymd } from './lib/dates';
  import { WEEKDAY_LABELS, WEEKDAY_LABELS_LONG, type TaskType, type TargetSegment } from './lib/types';

  export type TaskFormValues = {
    title: string;
    task_type: TaskType;
    weekdays: number[] | null;
    fixed_date: string | null;
    start_time: string | null;
    end_time: string | null;
    is_todo: boolean;
    target_per_week: number | null;
    target_segments: TargetSegment[] | null;
    show_in_upcoming: boolean;
    start_date: string | null;
    end_date: string | null;
  };

  type Initial = Partial<{
    title: string;
    task_type: TaskType;
    weekdays: number[];
    fixed_date: string;
    start: string;
    end: string;
    is_todo: boolean;
    target_per_week: number;
    target_segments: TargetSegment[];
    show_in_upcoming: boolean;
    start_date: string;
    end_date: string;
  }>;

  type Props = {
    open: boolean;
    initial?: Initial;
    editing?: boolean;
    onSubmit: (v: TaskFormValues) => void;
    onClose: () => void;
  };

  const { open, initial = {}, editing = false, onSubmit, onClose }: Props = $props();

  let dialog: HTMLDialogElement | null = $state(null);
  let titleInput: HTMLInputElement | null = $state(null);

  let title = $state('');
  let taskType = $state<TaskType>('recurring');
  let weekdays = $state<number[]>([]);
  let fixedDate = $state('');
  let start = $state('');
  let end = $state('');
  let isTodo = $state(false);
  let showInUpcoming = $state(true);
  let startDate = $state<string>('');
  let endDate = $state<string>('');
  // Weekly-goal: list of segments. Default = single segment covering every
  // day with target 3 (shorthand for the old flat target_per_week=3).
  let segments = $state<TargetSegment[]>([{ weekdays: [0, 1, 2, 3, 4, 5, 6], target: 3 }]);
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
      isTodo = snap.is_todo ?? false;
      showInUpcoming = snap.show_in_upcoming ?? true;
      startDate = snap.start_date ?? '';
      endDate = snap.end_date ?? '';
      if (snap.target_segments && snap.target_segments.length > 0) {
        segments = snap.target_segments.map((s) => ({
          weekdays: [...s.weekdays],
          target: s.target,
        }));
      } else if (snap.target_per_week) {
        segments = [{ weekdays: [0, 1, 2, 3, 4, 5, 6], target: snap.target_per_week }];
      } else {
        segments = [{ weekdays: [0, 1, 2, 3, 4, 5, 6], target: 3 }];
      }
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

  function toggleSegDay(idx: number, d: number) {
    const seg = segments[idx];
    const has = seg.weekdays.includes(d);
    const next = has ? seg.weekdays.filter((x) => x !== d) : [...seg.weekdays, d].sort((a, b) => a - b);
    segments[idx] = { ...seg, weekdays: next };
  }

  function addSegment() {
    // Pre-select the weekdays that no current segment covers, if any.
    const used = new Set(segments.flatMap((s) => s.weekdays));
    const free = [0, 1, 2, 3, 4, 5, 6].filter((d) => !used.has(d));
    segments = [...segments, { weekdays: free.length > 0 ? free : [], target: 1 }];
  }

  function removeSegment(idx: number) {
    segments = segments.filter((_, i) => i !== idx);
  }

  const TAB_ORDER: TaskType[] = ['single', 'recurring', 'weekly_goal', 'birthday'];

  function moveTab(dir: -1 | 1) {
    const i = TAB_ORDER.indexOf(taskType);
    taskType = TAB_ORDER[Math.min(TAB_ORDER.length - 1, Math.max(0, i + dir))];
  }

  function handleDayKey(e: KeyboardEvent, i: number) {
    if (e.key === ' ' || e.key === 'Enter') {
      e.preventDefault();
      toggleDay(i);
    }
  }

  function onDialogKey(e: KeyboardEvent) {
    if (e.key !== 'ArrowLeft' && e.key !== 'ArrowRight') return;
    const target = e.target as HTMLElement;

    if (target instanceof HTMLInputElement) {
      const type = target.type;
      if (type === 'date' || type === 'time' || type === 'number') return;
    }
    if (target instanceof HTMLTextAreaElement) return;

    if (target.classList.contains('day')) {
      e.preventDefault();
      const idx = parseInt(target.dataset.idx ?? '0', 10);
      const next = e.key === 'ArrowRight' ? Math.min(6, idx + 1) : Math.max(0, idx - 1);
      const el = dialog?.querySelector<HTMLButtonElement>(`.day[data-idx="${next}"]`);
      el?.focus();
      return;
    }

    e.preventDefault();
    moveTab(e.key === 'ArrowRight' ? 1 : -1);
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
    if (startDate && endDate && endDate < startDate) {
      localError = 'End date must be on or after start date';
      return;
    }
    if (taskType === 'recurring' && weekdays.length === 0) {
      localError = 'Pick at least one day';
      return;
    }
    if (taskType === 'birthday' && !fixedDate) {
      localError = 'Pick a date';
      return;
    }
    if (taskType === 'weekly_goal') {
      if (segments.length === 0) {
        localError = 'Add at least one segment';
        return;
      }
      const seen = new Set<number>();
      for (const s of segments) {
        if (s.weekdays.length === 0) {
          localError = 'Each segment needs at least one weekday';
          return;
        }
        if (s.target < 1) {
          localError = 'Targets must be at least 1';
          return;
        }
        for (const w of s.weekdays) {
          if (seen.has(w)) {
            localError = 'A weekday can belong to only one segment';
            return;
          }
          seen.add(w);
        }
      }
    }

    const isBirthday = taskType === 'birthday';
    const isBacklog = taskType === 'single' && !fixedDate;
    const isWeekly = taskType === 'weekly_goal';
    // Backlog and weekly_goal items must be actionable.
    const finalIsTodo = isBirthday ? false : isBacklog || isWeekly ? true : isTodo;

    // For weekly_goal: send target_segments. Keep target_per_week as the sum
    // for back-compat with anything that still reads the flat target.
    const flatSum = isWeekly ? segments.reduce((a, s) => a + s.target, 0) : null;

    onSubmit({
      title: t,
      task_type: taskType,
      weekdays: taskType === 'recurring' ? weekdays : null,
      fixed_date: taskType === 'single' ? (fixedDate || null) : taskType === 'birthday' ? fixedDate : null,
      start_time: isBirthday || isBacklog || isWeekly ? null : (start || null),
      end_time: isBirthday || isBacklog || isWeekly ? null : (start && end ? end : null),
      is_todo: finalIsTodo,
      target_per_week: flatSum,
      target_segments: isWeekly ? segments.map((s) => ({ weekdays: [...s.weekdays], target: s.target })) : null,
      show_in_upcoming: showInUpcoming,
      start_date: (taskType === 'recurring' || isWeekly) ? (startDate || null) : null,
      end_date: (taskType === 'recurring' || isWeekly) ? (endDate || null) : null,
    });
  }

  function handleBackdropClick(e: MouseEvent) {
    if (e.target === dialog) onClose();
  }
</script>

<dialog bind:this={dialog} onclose={onClose} onclick={handleBackdropClick} onkeydown={onDialogKey}>
  <form onsubmit={handleSubmit}>
    <h2>{editing ? 'Edit task' : 'New task'}</h2>

    <div class="type-tabs" role="tablist" tabindex="-1">
      <button
        type="button"
        class="type-tab"
        class:active={taskType === 'single'}
        role="tab"
        aria-selected={taskType === 'single'}
        tabindex={taskType === 'single' ? 0 : -1}
        data-type="single"
        onclick={() => (taskType = 'single')}
      >
        Single
      </button>
      <button
        type="button"
        class="type-tab"
        class:active={taskType === 'recurring'}
        role="tab"
        aria-selected={taskType === 'recurring'}
        tabindex={taskType === 'recurring' ? 0 : -1}
        data-type="recurring"
        onclick={() => (taskType = 'recurring')}
      >
        Recurring
      </button>
      <button
        type="button"
        class="type-tab"
        class:active={taskType === 'weekly_goal'}
        role="tab"
        aria-selected={taskType === 'weekly_goal'}
        tabindex={taskType === 'weekly_goal' ? 0 : -1}
        data-type="weekly_goal"
        onclick={() => (taskType = 'weekly_goal')}
      >
        Weekly
      </button>
      <button
        type="button"
        class="type-tab"
        class:active={taskType === 'birthday'}
        role="tab"
        aria-selected={taskType === 'birthday'}
        tabindex={taskType === 'birthday' ? 0 : -1}
        data-type="birthday"
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
              data-idx={i}
              onclick={() => toggleDay(i)}
              onkeydown={(e) => handleDayKey(e, i)}
            >
              {label}
            </button>
          {/each}
        </div>
      </div>
    {:else if taskType === 'weekly_goal'}
      <div class="field">
        <span>Segments</span>
        {#each segments as seg, idx (idx)}
          <div class="segment">
            <div class="days small">
              {#each WEEKDAY_LABELS as label, i}
                <button
                  type="button"
                  class="day"
                  class:on={seg.weekdays.includes(i)}
                  aria-label={WEEKDAY_LABELS_LONG[i]}
                  aria-pressed={seg.weekdays.includes(i)}
                  onclick={() => toggleSegDay(idx, i)}
                >
                  {label}
                </button>
              {/each}
            </div>
            <div class="segment-controls">
              <label class="inline">
                <span>Target</span>
                <input
                  type="number"
                  min="1"
                  max="99"
                  bind:value={segments[idx].target}
                />
              </label>
              {#if segments.length > 1}
                <button
                  type="button"
                  class="del"
                  aria-label="Remove segment"
                  title="Remove"
                  onclick={() => removeSegment(idx)}
                >
                  ×
                </button>
              {/if}
            </div>
          </div>
        {/each}
        <button type="button" class="add-seg" onclick={addSegment}>+ Add segment</button>
      </div>
      <p class="hint">
        Split the week into buckets, e.g. Mon-Thu × 2 and Fri-Sun × 1. Each
        bucket counts its own completions.
      </p>
    {:else}
      <label class="field">
        <span>{taskType === 'birthday' ? 'Birth date' : 'Date'}</span>
        <input
          type="date"
          bind:value={fixedDate}
          required={taskType === 'birthday'}
        />
      </label>
      {#if taskType === 'single' && !fixedDate}
        <p class="hint">No date → goes to the backlog. Always actionable (todo).</p>
      {/if}
    {/if}

    {#if taskType === 'recurring' || (taskType === 'single' && fixedDate)}
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

      <label class="todo">
        <input type="checkbox" bind:checked={isTodo} />
        <span>To-do (show a checkbox to mark it done)</span>
      </label>
    {:else if taskType === 'birthday'}
      <p class="hint">Birthdays repeat every year on the same month and day.</p>
    {/if}

    <label class="todo">
      <input type="checkbox" bind:checked={showInUpcoming} />
      <span>Show in upcoming panel</span>
    </label>

    {#if taskType === 'recurring' || taskType === 'weekly_goal'}
      <div class="row">
        <label class="field">
          <span>Active from</span>
          <input type="date" bind:value={startDate} />
        </label>
        <label class="field">
          <span>Until</span>
          <input type="date" bind:value={endDate} />
        </label>
      </div>
      <div class="end-row">
        <button
          type="button"
          class="end-now"
          onclick={() => (endDate = ymd(new Date()))}
          title="Stop the task at the end of today"
        >
          End today
        </button>
        {#if endDate}
          <button
            type="button"
            class="end-now ghost"
            onclick={() => (endDate = '')}
            title="Clear end date"
          >
            Clear
          </button>
        {/if}
      </div>
      <p class="hint">
        Leave “Active from” empty to default to today on creation. Set “Until”
        instead of deleting when something ends — history and streaks stay
        intact.
      </p>
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
  .days.small .day {
    padding: 0.35rem 0;
    font-size: 0.72rem;
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

  .segment {
    display: flex;
    flex-direction: column;
    gap: 0.4rem;
    background: var(--bg-3);
    border: 1px solid var(--border);
    border-radius: 8px;
    padding: 0.5rem;
  }
  .segment-controls {
    display: flex;
    align-items: center;
    gap: 0.5rem;
  }
  .inline {
    display: flex;
    align-items: center;
    gap: 0.4rem;
    flex: 1;
    font-size: 0.78rem;
    color: var(--fg-muted);
    text-transform: uppercase;
    letter-spacing: 0.05em;
  }
  .inline input {
    width: 4rem;
    text-transform: none;
    letter-spacing: normal;
    font-size: 0.95rem;
    color: var(--fg);
  }
  .del {
    background: transparent;
    color: var(--fg-muted);
    border: 1px solid var(--border);
    width: 28px;
    height: 28px;
    border-radius: 6px;
    cursor: pointer;
    font-size: 1rem;
    line-height: 1;
  }
  .del:hover {
    color: var(--danger);
    border-color: var(--danger);
  }
  .add-seg {
    align-self: flex-start;
    background: transparent;
    color: var(--accent);
    border: 1px dashed color-mix(in srgb, var(--accent) 50%, transparent);
    padding: 0.4rem 0.7rem;
    border-radius: 6px;
    cursor: pointer;
    font: inherit;
    font-size: 0.8rem;
    text-transform: none;
    letter-spacing: normal;
  }
  .add-seg:hover {
    background: color-mix(in srgb, var(--accent) 12%, transparent);
  }

  .row {
    display: flex;
    gap: 0.75rem;
  }
  .row .field {
    flex: 1;
  }

  .todo {
    display: flex;
    align-items: center;
    gap: 0.65rem;
    padding: 0.65rem 0.8rem;
    background: var(--bg-3);
    border: 1px solid var(--border);
    border-radius: 8px;
    font-size: 0.9rem;
    color: var(--fg);
    cursor: pointer;
    user-select: none;
    transition: border-color 100ms ease, background-color 100ms ease;
  }
  .todo:hover {
    border-color: var(--accent);
  }
  .todo:has(input:checked) {
    background: color-mix(in srgb, var(--accent) 18%, var(--bg-3));
    border-color: var(--accent);
  }
  .todo input {
    width: 20px;
    height: 20px;
    accent-color: var(--accent);
    flex: none;
    cursor: pointer;
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

  .end-row {
    display: flex;
    gap: 0.4rem;
  }
  .end-now {
    background: var(--bg-3);
    border: 1px solid var(--border);
    color: var(--fg);
    padding: 0.4rem 0.7rem;
    border-radius: 6px;
    cursor: pointer;
    font: inherit;
    font-size: 0.8rem;
  }
  .end-now:hover {
    border-color: var(--accent);
    color: var(--accent);
  }
  .end-now.ghost {
    color: var(--fg-muted);
  }
</style>
