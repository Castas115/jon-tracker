# Jon Tracker

App self-hosted en Raspberry Pi para gestionar tareas semanales recurrentes y calendario. Acceso desde navegador (PWA instalable) y, en fase 2, app + widget Android nativos.

## Stack

- **Backend**: FastAPI + SQLAlchemy + SQLite (Python 3.12, gestionado con uv)
- **Frontend**: Svelte 5 + Vite + TypeScript, PWA via `vite-plugin-pwa`
- **Deploy**: imagen Docker multi-stage (frontend build → static → servido por FastAPI)
- **Orquestación**: Docker Compose en raspi
- **Acceso remoto**: Tailscale (tailnet privado)
- **Sync calendario**: Google Calendar API (TBD)

## Estructura

```
.
├── Dockerfile              # multi-stage: node frontend + python backend
├── docker-compose.yml
├── backend/
│   ├── pyproject.toml
│   └── app/
│       ├── main.py         # FastAPI + static mount
│       ├── config.py       # settings
│       ├── db.py           # SQLAlchemy engine
│       ├── models.py       # Task, TaskCompletion
│       ├── schemas.py      # Pydantic DTOs
│       └── routers/
│           └── tasks.py    # CRUD + toggle
└── frontend/
    ├── package.json
    ├── vite.config.ts      # PWA + dev proxy a backend
    └── src/
        ├── App.svelte      # vista semanal
        ├── lib/api.ts      # cliente HTTP
        └── lib/types.ts
```

## Desarrollo local

Dos procesos: backend en `:8000`, vite en `:5173` (proxea `/tasks` y `/health` al backend).

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

UI dev: http://localhost:5173 · API docs: http://localhost:8000/docs

## Deploy raspi

```fish
./scripts/deploy.sh                  # full pipeline
./scripts/deploy.sh --skip-checks    # rápido sin lint
./scripts/deploy.sh --no-build       # solo restart
```

El script hace: lint backend (ruff) → rsync repo → `docker compose up -d --build` en raspi → health probe.

Acceso vía tailnet: `http://raspi:8000` (cuando el bind sea `0.0.0.0` o se sirva con `tailscale serve`).

## MVP scope

- [x] CRUD tareas con `weekday` (lun=0 .. dom=6)
- [x] Toggle completed por fecha
- [x] Vista semanal agrupada por día con día actual destacado
- [x] PWA instalable
- [ ] Vista calendario con eventos Google Calendar (read-only)
- [ ] Auth (Tailscale-only acceso, sin login propio)
- [ ] Widget Android nativo (fase 2)
