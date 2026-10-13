# Finanssi 2: notes for coding agents

## Project status and active work

- Current features, decisions and todos: [backend status](finanssi2-backend/STATUS.md) and
  [frontend status](finanssi2-web/STATUS.md).
- Single-player debug mode is implemented. Remaining developer-run acceptance checks and configuration are recorded in the
  backend/frontend status notes; use those as the session handoff.

## Frontend tooling in the sandbox

- Deno is installed at `/home/agent/.deno/bin/deno`, but that directory may be missing from the command environment's `PATH`.
  Before running frontend tasks, use `export PATH="$HOME/.deno/bin:$PATH"` or invoke the binary by its full path.
- If the local binary is missing, install Deno for the sandbox user with `curl -fsSL https://deno.land/install.sh | sh`, then add
  `$HOME/.deno/bin` to `PATH`. Frontend tasks are defined in `finanssi2-web/deno.json`.

## Deployment model

- One frontend, one backend instance and one PostgreSQL database. There is no horizontal scaling and none is planned.
- Don't design for multiple backend instances: no cross-instance race handling, distributed locks or shared broker. In-process
  state and locking and the in-memory STOMP broker are fine. Concurrent requests within the one backend (two players, two tabs)
  still need handling, e.g. optimistic locking.

## Commits

- Commit only when explicitly asked. Never push.
- One-line message: what changed, not how. No body, no trailers (no `Co-Authored-By`), no implementation details, no notes on branch, push
  or test state.
- Prefix `frontend:`, `backend:` or `infra:`; no prefix when a commit touches all.
- Lowercase, imperative: `frontend: update node types dep`, `authenticate chat websocket, replace react-stomp-hooks`
