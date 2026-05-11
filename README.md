# Jon Tracker

Self-hosted Raspberry Pi app for managing recurring weekly tasks and a calendar. Accessed from the browser as an installable PWA today; native Android app + widget planned for phase two.

## Stack

- **Backend**: FastAPI + SQLAlchemy + SQLite (Python 3.12, managed with uv)
- **Frontend**: Svelte 5 + Vite + TypeScript, PWA via `vite-plugin-pwa`
- **Deploy**: multi-stage Docker image (frontend build → static → served by FastAPI)
- **Orchestration**: Docker Compose on the pi
- **Remote access**: Tailscale (private tailnet only — no public ingress)
- **Calendar sync**: Google Calendar API (TBD)

## Layout

```
.
├── Dockerfile              # multi-stage: node frontend + python backend
├── docker-compose.yml
├── backend/
│   ├── pyproject.toml
│   └── app/
│       ├── main.py         # FastAPI + static mount
│       ├── config.py       # settings
│       ├── db.py           # SQLAlchemy engine + light migration
│       ├── models.py       # Task, TaskCompletion
│       ├── schemas.py      # Pydantic DTOs
│       └── routers/
│           └── tasks.py    # CRUD + toggle
└── frontend/
    ├── package.json
    ├── vite.config.ts      # PWA + dev proxy to the backend
    └── src/
        ├── App.svelte      # tabs, theme, add form
        ├── WeekGrid.svelte # hourly week view
        ├── MonthView.svelte
        ├── lib/api.ts      # HTTP client
        ├── lib/types.ts
        ├── lib/dates.ts
        └── lib/theme.ts
```

## Local development

Two processes: backend on `:8000`, vite on `:5173` (proxies `/tasks` and `/health` to the backend).

```fish
# terminal 1: backend
cd backend
uv sync
uv run uvicorn app.main:app --reload

# terminal 2: frontend
cd frontend
npm install
npm run dev
```

Dev UI: http://localhost:5173 · API docs: http://localhost:8000/docs

## Deploy to the pi

```fish
./scripts/deploy.sh                  # full pipeline
./scripts/deploy.sh --skip-checks    # quick, no lint
./scripts/deploy.sh --no-build       # restart only
```

The script: lints the backend (ruff) → rsyncs the repo → runs `docker compose up -d --build` on the pi → probes `/health`.

Tailnet access: `http://pi:8000` (or `https://pi.<tailnet>.ts.net` once `tailscale serve` is wired up).

## Google Calendar (read-only)

Sync is **one-way**: events created in Google Calendar appear in the app; tasks created in the app stay local and are not pushed to Google. The integration is a plain `.ics` feed pull — no OAuth, no Cloud Console.

Setup:

1. Open Google Calendar in the browser → ⚙️ Settings → click your calendar in the left sidebar.
2. Scroll to **Integrate calendar** → copy the URL under *Secret address in iCal format* (the one ending in `/basic.ics`).
3. On the pi, create `~/jon-tracker/.env` (or append):
   ```fish
   ssh pi 'echo "ICS_URL=PASTE_THE_URL_HERE" >> ~/jon-tracker/.env'
   ssh pi 'chmod 600 ~/jon-tracker/.env'
   ssh pi 'cd ~/jon-tracker && docker compose up -d'
   ```
4. Reload `http://pi:8000` — events render as dashed blocks in the week grid.

The URL is gitignored (matches `.env`). Treat it like a password: anyone with the URL can read your calendar. Reset it from Google Calendar settings → "Reset" if it leaks.

## MVP scope

- [x] Task CRUD with weekday (Mon=0 … Sun=6)
- [x] Optional `start_time` / `end_time` for hourly blocks
- [x] Per-date completion toggle
- [x] Week grid with hour rows + all-day row
- [x] Month grid with day-detail panel
- [x] Day grid with single-column nav
- [x] Dark / light theme toggle (persisted)
- [x] Installable PWA
- [x] Google Calendar events overlay (read-only via ICS feed)
- [x] Birthdays (local + detected from feed) with 🎂
- [x] Recurring, single-date and birthday task types
- [x] To-do flag (checkbox + completion only on actionable tasks)
- [x] Vim-style keyboard nav (hjkl + count prefix)
- [ ] HTTPS via `tailscale serve`

## Task model

Every task has a `task_type` and an orthogonal `is_todo` flag.

| `task_type` | Fields                                       | When it appears                        |
| ----------- | -------------------------------------------- | -------------------------------------- |
| `recurring` | `weekdays: int[]`, optional `start`/`end`    | Every matching weekday (Mon=0 … Sun=6) |
| `single`    | `fixed_date`, optional `start`/`end`         | Only on that date                      |
| `birthday`  | `fixed_date` (any year — month + day matter) | Yearly. No times. `is_todo=false`      |

`is_todo` (default `false`):

- `true` → renders a checkbox. Completion tracked per-date (`task_completions` rows).
- `false` → display-only block. No checkbox, no done state.
- Always `false` for `birthday`.

## Backlog

- [ ] **Gym section** — workout templates, set/rep tracking, history
- [ ] **Daily checklist** — morning routine / habits panel separate from tasks
- [ ] **Android app** — native client wrapping the PWA + offline cache
- [ ] **Android widgets** — home-screen tile showing today's tasks + quick toggle
