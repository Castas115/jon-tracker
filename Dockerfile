# syntax=docker/dockerfile:1.7

# ---- frontend build ----
FROM mirror.gcr.io/library/node:22-slim AS frontend
WORKDIR /web
COPY frontend/package.json frontend/package-lock.json* ./
RUN npm install --no-audit --no-fund
COPY frontend/ ./
RUN npm run build

# ---- backend ----
FROM mirror.gcr.io/library/python:3.12-slim AS backend
ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    UV_LINK_MODE=copy \
    UV_COMPILE_BYTECODE=1

COPY --from=ghcr.io/astral-sh/uv:0.5.4 /uv /uvx /usr/local/bin/

# Node + Claude Code CLI for the worker container. Backend container
# never invokes claude but shares this image; the extra layers add
# ~150 MB which is acceptable for a self-hosted pi setup.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl ca-certificates gnupg \
    && curl -fsSL https://deb.nodesource.com/setup_22.x | bash - \
    && apt-get install -y --no-install-recommends nodejs \
    && npm install -g @anthropic-ai/claude-code \
    && apt-get purge -y --auto-remove curl gnupg \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY backend/pyproject.toml backend/uv.lock* ./
RUN uv sync --no-install-project --no-dev

COPY backend/app ./app
COPY worker ./worker
COPY --from=frontend /web/dist ./static

EXPOSE 8000

CMD ["uv", "run", "--no-dev", "uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
