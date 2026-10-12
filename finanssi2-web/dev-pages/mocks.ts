// Shared setup for the test pages: a fake logged-in user and a fake backend, so pages work without a Firebase login or a running backend
import type { StompConnection } from "../src/realtime/StompContext.tsx"
import { matti, profileOf, publicProfiles, stranger, tester } from "./people.ts"

/** A chat row as the backend sends it: the sender is a user id, resolved to a name and avatar through /api/users */
export type FakeChatMessage = {
    id: string
    userId: string
    message: string
    timestamp: number
}

// Includes a sender the fake backend has no profile for, like a deleted account
const fakeSenders = [{ userId: tester.id }, { userId: matti.id }, { userId: stranger.id }]

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
    }))

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
        const url = new URL(String(input).replace(/^undefined/, ""), location.origin)
        if (url.pathname === "/api/me") return Promise.resolve(Response.json(profileOf(tester, false)))
        if (url.pathname === "/api/users") return Promise.resolve(Response.json(publicProfiles(url)))
        if (init?.method === "POST") {
            sentMessages.push(JSON.parse(String(init.body)))
            console.info("[fake backend] POST /api/chat", init.body)
            return Promise.resolve(new Response(null, { status: 200 }))
        }
        const params = url.searchParams
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
        const subscribers = fakeSubscriptions.get(destination) ?? new Set<(body: string) => void>()
        fakeSubscriptions.set(destination, subscribers)
        subscribers.add(onMessage)
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
