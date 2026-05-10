# Jon Tracker — Claude project notes

## Stack quick ref

- Backend: FastAPI + SQLAlchemy + SQLite, managed with `uv`
- Frontend: Svelte 5 (runes) + Vite + TypeScript, PWA via `vite-plugin-pwa`
- Single Docker image (multi-stage) — frontend build copied as static into the
  Python image. Compose runs it on the pi.
- Remote access via Tailscale; the pi is `pi` in `~/.ssh/config` (MagicDNS).
- Dev compose (`docker-compose.dev.yml`) gives hot reload for both sides.

## Workflow rules

### Commit cadence
Commit per logical change as soon as it's coherent and the code typechecks /
builds. Don't wait for the user to ask. Never bundle unrelated concerns into
one commit. Push only when the user asks.

Good split points:
- Backend model / schema / migration → 1 commit
- Frontend feature (new component + wiring + styles) → 1 commit
- Config / settings / scripts → 1 commit
- Translation / rename / refactor → 1 commit (separate from features)

### Commit messages
- Subject: `area: imperative summary` (lower-case, ≤ 50 chars)
- Body only when the *why* isn't obvious from the diff or subject
- No `Co-Authored-By` trailer (already disabled via `attribution.commit`
  in `.claude/settings.json`)
- Match the existing log style — short, focused, one concern per commit.

### Before committing
- Frontend changes: run `npm run check` from `frontend/` (zero errors expected)
- Backend changes: ensure no syntax errors; backend typechecking optional
  (Pyright complaints about missing imports outside the docker venv are noise)
- Don't commit `.venv`, `node_modules`, `dist`, `data/`, `secrets/` — already
  in `.gitignore`
- Never commit `.env` or anything with secrets

### Deploys
- `./scripts/deploy.sh` lints the backend, rsyncs to `pi`, rebuilds the
  container, runs the health probe. Use `--skip-checks` to skip lint,
  `--no-build` to just restart.
- The pi's docker daemon uses `mirror.gcr.io` as a Docker Hub pull-through
  cache (Cloudflare R2 routing was unreliable from the LAN). Don't switch
  base images to ones not mirrored there without testing.

## Conventions

- App language: English. Don't reintroduce Spanish strings in code.
- Theme: dark default with pure black background (`#000000`) — keep it OLED-
  friendly. Light theme uses whites with a dimmer accent.
- Weekdays: 0 = Monday … 6 = Sunday. JS native Sunday=0 is normalized in
  `lib/dates.ts:weekdayMonFirst`.
- Time format: `"HH:MM"` strings end-to-end; backend validates with regex.

## Things that are intentional

- The pi's NixOS `networking.hostName` stays `nixos`; the Tailscale device is
  renamed to `pi` from the admin console. Don't try to fix the hostname unless
  asked.
- The `weekday` (singular) column on `tasks` is legacy — kept for backfill,
  not read by the model. New code uses `weekdays` (JSON list).
