import type { User } from "firebase/auth"
import type { z } from "zod/mini"

import { apiUrl } from "../env.ts"
import { t } from "../i18n/index.ts"

export type { Card, CommandOptions, Game, GameBoardData, GamePlayer, GameSquare, GameView, PendingDecision, PublicProfile, UserProfile } from "./schemas.ts"

export class GameApiError extends Error {
    constructor(message: string, public status: number, public code?: string) {
        super(message)
    }
}

export const avatarSrc = (avatar: string | null | undefined) => {
    if (!avatar) return undefined
    if (!avatar.startsWith("/")) return avatar
    return `${apiUrl()}${avatar}`
}

export async function gameApi<T>(user: User, path: string, init: RequestInit = {}): Promise<T> {
    const token = await user.getIdToken()
    // A Headers instance, an array or a plain object from the caller all merge the same way
    const headers = new Headers(init.headers)
    headers.set("Authorization", `Bearer ${token}`)
    if (init.body && !(typeof FormData !== "undefined" && init.body instanceof FormData) && !headers.has("Content-Type")) {
        headers.set("Content-Type", "application/json")
    }
    const response = await fetch(`${apiUrl()}${path}`, { ...init, headers })
    if (!response.ok) {
        const body = await response.json().catch(() => null)
        throw new GameApiError(body?.detail ?? t("api.requestFailed", { status: response.status }), response.status, body?.code)
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
        throw new GameApiError(t("api.unexpectedResponse"), 502)
    }
    return result.data
}

/** A path segment from an id, safe to put in a URL */
export const segment = (id: string) => encodeURIComponent(id)
