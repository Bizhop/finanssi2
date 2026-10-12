import { assertEquals, assertInstanceOf } from "@std/assert"
import type { User } from "firebase/auth"
import { gameApi, GameApiError } from "./gameApi.ts"

const user = { getIdToken: () => Promise.resolve("verified-token") } as User

Deno.test("successful empty REST responses cover deletion and ordinary leave", async () => {
    const originalFetch = globalThis.fetch
    try {
        for (const status of [200, 204]) {
            globalThis.fetch = (_input, init) => {
                if (new Headers(init?.headers).get("Authorization") !== "Bearer verified-token") throw new Error("Missing account token")
                if (init?.method !== "DELETE") throw new Error("Missing delete method")
                return Promise.resolve(new Response(null, { status }))
            }
            assertEquals(await gameApi<void>(user, "/api/debug/games/game", { method: "DELETE" }), undefined)
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
            assertInstanceOf(failure, GameApiError)
            assertEquals([failure.status, failure.message, calls], [status, "Reload required", 1])
        }
    } finally {
        globalThis.fetch = originalFetch
    }
})

Deno.test("caller headers of any form are merged with the token and the JSON content type", async () => {
    const originalFetch = globalThis.fetch
    try {
        let seen: Headers | undefined
        globalThis.fetch = (_input, init) => {
            seen = init?.headers as Headers
            return Promise.resolve(new Response(null, { status: 204 }))
        }
        await gameApi<void>(user, "/api/x", { method: "POST", body: "{}", headers: new Headers({ "X-Test": "1" }) })
        assertEquals(seen?.get("X-Test"), "1")
        assertEquals(seen?.get("Authorization"), "Bearer verified-token")
        assertEquals(seen?.get("Content-Type"), "application/json")
    } finally {
        globalThis.fetch = originalFetch
    }
})
