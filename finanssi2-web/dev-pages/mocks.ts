// Shared setup for the test pages: a fake logged-in user and a fake backend, so pages work without a Firebase login or a running backend
import { z } from "zod/mini"
import { en } from "zod/locales"

import { auth } from "../src/components/firebase.ts"
import type { StompConnection } from "../src/components/StompContext.tsx"

// Same as src/index.tsx
z.config(en())

export type FakeChatMessage = {
    id: string
    username: string
    name?: string | null
    message: string
    timestamp: number
    photoUrl: string
}

// Includes a sender without a name, like messages saved before the backend stored names
const fakeSenders = [
    { username: "maija.meikalainen@example.com", name: "Maija Meikäläinen" },
    { username: "matti.meikalainen@example.com", name: "Matti Meikäläinen" },
    { username: "tester@example.com", name: null },
]

// Fixed-width decimal ids sort in database insertion order.
let lastFakeId = 0
export const nextFakeId = () => (++lastFakeId).toString(16).padStart(24, "0")

/** Oldest first */
export const fakeChatMessages = (count: number): FakeChatMessage[] =>
    Array.from({ length: count }, (_, i) => ({
        id: nextFakeId(),
        ...fakeSenders[i % fakeSenders.length],
        message: i % 7 === 3
            ? `Message number ${
                i + 1
            }, a longer one that wraps onto a second line to show how the name prefix, the avatar and the timestamp tooltip behave when a message does not fit on one line`
            : `Message number ${i + 1}`,
        timestamp: Date.now() - (count - i) * 60_000,
        photoUrl: "",
    }))

/** Makes the app see a logged-in user; only getIdToken() is used by the app */
export const fakeLogin = () => {
    const fakeUser = {
        uid: "tester",
        email: "tester@example.com",
        displayName: "Maija Meikäläinen",
        photoURL: null,
        getIdToken: () => Promise.resolve("fake-token"),
    }
    // deno-lint-ignore no-explicit-any
    const fakeAuth = auth as any
    fakeAuth.onAuthStateChanged = (callback: (user: unknown) => void) => {
        callback(fakeUser)
        return () => {}
    }
    fakeAuth.signOut = () => Promise.resolve()
}

// Messages the fake backend has "saved", oldest first
let fakeChatHistory: FakeChatMessage[] = []

/**
 * Answers GET /api/chat pages (?before=<id>&size=N, newest first) from the given history, after a delay so lazy loading can be seen,
 * and records POSTed messages instead of sending them
 */
export const fakeBackend = (chatHistory: FakeChatMessage[], delayMs = 300) => {
    fakeChatHistory = chatHistory
    const sentMessages: unknown[] = []
    globalThis.fetch = (input, init) => {
        if (init?.method === "POST") {
            sentMessages.push(JSON.parse(String(init.body)))
            console.info("[fake backend] POST /api/chat", init.body)
            return Promise.resolve(new Response(null, { status: 200 }))
        }
        const params = new URL(String(input)).searchParams
        const before = params.get("before")
        const size = Number(params.get("size") ?? 20)
        const page = fakeChatHistory
            .filter((message) => before === null || message.id < before)
            .reverse()
            .slice(0, size)
        console.info(`[fake backend] GET /api/chat?${params}: ${page.length} messages`)
        return new Promise((resolve) => setTimeout(() => resolve(new Response(JSON.stringify(page), { status: 200 })), delayMs))
    }
    return sentMessages
}

// Subscriptions on the fake websocket: destination -> callbacks
const fakeSubscriptions = new Map<string, Set<(body: string) => void>>()

/** Value for StompContext.Provider: a fake connected (or disconnected) websocket */
export const fakeStompConnection = (connected: boolean): StompConnection => ({
    connected,
    subscribe: (destination, onMessage) => {
        if (!connected) return () => {}
        if (!fakeSubscriptions.has(destination)) fakeSubscriptions.set(destination, new Set())
        fakeSubscriptions.get(destination)!.add(onMessage)
        return () => fakeSubscriptions.get(destination)?.delete(onMessage)
    },
})

/**
 * Simulates another user's message: saved by the fake backend, and pushed over the fake websocket to current subscribers.
 * While disconnected there are none, so it is only in the history, like a message missed during a real disconnect.
 */
export const receiveFakeChatMessage = (message: FakeChatMessage) => {
    fakeChatHistory.push(message)
    fakeSubscriptions.get("/topic/chat")?.forEach((onMessage) => onMessage(JSON.stringify(message)))
}
