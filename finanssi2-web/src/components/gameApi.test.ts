import type { User } from "firebase/auth"
import { gameApi, GameApiError } from "./gameApi.ts"

const user = { getIdToken: () => Promise.resolve("verified-token") } as User

Deno.test("successful empty REST responses cover deletion and ordinary leave", async () => {
    const originalFetch = globalThis.fetch
    try {
        for (const status of [200, 204]) {
            globalThis.fetch = (_input, init) => {
                const headers = init?.headers as Record<string, string>
                if (headers.Authorization !== "Bearer verified-token") throw new Error("Missing account token")
                if (init?.method !== "DELETE") throw new Error("Missing delete method")
                return Promise.resolve(new Response(null, { status }))
            }
            if (await gameApi<void>(user, "/api/debug/games/game", { method: "DELETE" }) !== undefined) {
                throw new Error("Empty success must return undefined")
            }
        }
    } finally {
        globalThis.fetch = originalFetch
    }
})

Deno.test("REST failures preserve conflict and forbidden status without replaying requests", async () => {
    const originalFetch = globalThis.fetch
    try {
        for (const status of [403, 409]) {
            let calls = 0
            globalThis.fetch = () => {
                calls++
                return Promise.resolve(Response.json({ detail: "Reload required" }, { status }))
            }
            let failure: unknown
            try {
                await gameApi(user, "/api/debug/games/game/commands", {
                    method: "POST",
                    body: JSON.stringify({ actor: "seat", expectedVersion: 1, command: { type: "Roll" }, dice: [3] }),
                })
            } catch (reason) {
                failure = reason
            }
            if (!(failure instanceof GameApiError) || failure.status !== status || failure.message !== "Reload required" || calls !== 1) {
                throw new Error("Expected one request and a status-bearing error")
            }
        }
    } finally {
        globalThis.fetch = originalFetch
    }
})
