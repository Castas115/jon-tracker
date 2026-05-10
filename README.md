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

Sync is **one-way**: events created in Google Calendar appear in the app; tasks created in the app stay local and are not pushed to Google.

One-time setup:

1. Open https://console.cloud.google.com → create or select a project.
2. Enable the **Google Calendar API** for the project.
3. *APIs & Services → OAuth consent screen* → External, add your email as a test user.
4. *Credentials → Create credentials → OAuth client ID → Web application*. Authorised redirect URI: `http://pi:8000/auth/google/callback` (or whatever `GOOGLE_REDIRECT_URI` is set to). Download the JSON.
5. Copy the file to `~/jon-tracker/secrets/credentials.json` **on the pi** (the deploy script intentionally excludes `secrets/` from rsync). Example:
   ```fish
   ssh pi 'mkdir -p ~/jon-tracker/secrets'
   scp credentials.json pi:~/jon-tracker/secrets/credentials.json
   ssh pi 'cd ~/jon-tracker && docker compose restart backend'
   ```
6. Browse to `http://pi:8000` and click **Connect Google** in the header. The token is stored in `./data/google_token.json`.

Disconnect with the green `G ✓` button (clears the local token; revoke fully from your Google account if needed).

## MVP scope

- [x] Task CRUD with weekday (Mon=0 … Sun=6)
- [x] Optional `start_time` / `end_time` for hourly blocks
- [x] Per-date completion toggle
- [x] Week grid with hour rows + all-day row
- [x] Month grid with day-detail panel
- [x] Dark / light theme toggle (persisted)
- [x] Installable PWA
- [ ] Google Calendar events overlay (read-only)
- [ ] HTTPS via `tailscale serve`
- [ ] Native Android widget (phase 2)
