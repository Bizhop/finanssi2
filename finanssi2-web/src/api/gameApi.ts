import type { User } from "firebase/auth"
import type { z } from "zod/mini"

export type { Card, CommandOptions, Game, GameBoardData, GamePlayer, GameSquare, GameView, PendingDecision, PublicProfile, UserProfile } from "./schemas.ts"

export class GameApiError extends Error {
    constructor(message: string, public status: number, public code?: string) {
        super(message)
    }
}

export const avatarSrc = (avatar: string | null | undefined) => {
    if (!avatar) return undefined
    if (!avatar.startsWith("/")) return avatar
    return `${(import.meta.env?.VITE_FINANSSI_API_URL ?? "").replace(/\/$/, "")}${avatar}`
}

export async function gameApi<T>(user: User, path: string, init: RequestInit = {}): Promise<T> {
    const token = await user.getIdToken()
    const response = await fetch(`${(import.meta.env?.VITE_FINANSSI_API_URL ?? "")}${path}`, {
        ...init,
        headers: {
            Authorization: `Bearer ${token}`,
            ...(init.body && !(typeof FormData !== "undefined" && init.body instanceof FormData) ? { "Content-Type": "application/json" } : {}),
            ...init.headers,
        },
    })
    if (!response.ok) {
        const body = await response.json().catch(() => null)
        throw new GameApiError(body?.detail ?? `Request failed (${response.status})`, response.status, body?.code)
    }
    if (response.status === 204) return undefined as T
    const body = await response.text()
    return body ? JSON.parse(body) as T : undefined as T
}

/** Like gameApi, but the response must match the schema; what comes back is typed from it, not from a cast */
export async function gameApiParsed<S extends z.ZodMiniType>(user: User, path: string, schema: S, init: RequestInit = {}): Promise<z.infer<S>> {
    const body = await gameApi<unknown>(user, path, init)
    const result = schema.safeParse(body)
    if (!result.success) {
        console.error(`Unexpected response from ${path}`, result.error)
        throw new GameApiError("The server sent a response this version of the app does not understand. Reload the page.", 502)
    }
    return result.data
}

/** A path segment from an id, safe to put in a URL */
export const segment = (id: string) => encodeURIComponent(id)
