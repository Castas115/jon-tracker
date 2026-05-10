# Jon Tracker

App self-hosted en Raspberry Pi para gestionar calendario semanal y tareas recurrentes. Acceso desde navegador (PWA) y app Android (fase 2).

## Stack

- **Backend**: FastAPI + SQLite, Python 3.12 (uv)
- **Frontend**: SvelteKit PWA (TBD)
- **Deploy**: Docker Compose en raspi
- **Acceso remoto**: Tailscale (tailnet privado)
- **Sync calendario**: Google Calendar API (OAuth2)

## Estructura

```
.
├── backend/           # API FastAPI
├── frontend/          # PWA SvelteKit (TBD)
├── docker-compose.yml # orquestación raspi
└── Caddyfile          # reverse proxy + HTTPS via Tailscale
```

## Desarrollo local

```fish
cd backend
uv sync
uv run uvicorn app.main:app --reload
```

API en `http://localhost:8000`, docs en `/docs`.

## Deploy raspi

```fish
# desde tu PC
rsync -av ./ raspi:~/jon-tracker/
ssh raspi 'cd ~/jon-tracker && docker compose up -d'
```

Acceso vía tailnet: `http://raspi:8000` o `https://raspi.<tailnet>.ts.net` con `tailscale serve`.

## MVP scope

- [ ] Tareas recurrentes semanales (lunes–domingo)
- [ ] Marcar completada/incompleta por día
- [ ] Vista calendario con eventos Google Calendar (read-only)
- [ ] PWA instalable en Android
- [ ] Widget Android nativo (fase 2)
