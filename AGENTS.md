# Finanssi 2: notes for coding agents

## Project status and active work

- Current features, decisions and todos: [backend status](finanssi2-backend/STATUS.md) and
  [frontend status](finanssi2-web/STATUS.md).
- Single-player debug mode is implemented. Remaining developer-run acceptance checks and configuration are recorded in the
  backend/frontend status notes; use those as the session handoff.

## Deployment model

- One frontend, one backend instance and one MongoDB. There is no horizontal scaling and none is planned.
- Don't design for multiple backend instances: no cross-instance race handling, distributed locks or shared broker. In-process
  state and locking and the in-memory STOMP broker are fine. Concurrent requests within the one backend (two players, two tabs)
  still need handling, e.g. optimistic locking.

## Commits

- Commit only when explicitly asked. Never push.
- One-line message: what changed, not how. No body, no trailers (no `Co-Authored-By`), no implementation details, no notes on branch, push
  or test state.
- Prefix `frontend:` or `backend:`; no prefix when a commit touches both.
- Lowercase, imperative: `frontend: update node types dep`, `authenticate chat websocket, replace react-stomp-hooks`
