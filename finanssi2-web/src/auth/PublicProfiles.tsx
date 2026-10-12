import { createContext, type ReactNode, useCallback, useContext, useEffect, useMemo, useRef, useState } from "react"

import { useCurrentUser } from "./CurrentUserContext.tsx"
import { gameApiParsed, type PublicProfile } from "../api/gameApi.ts"
import { PublicProfileSchema } from "../api/schemas.ts"
import { z } from "zod/mini"

const REFRESH_MS = 15_000
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i

type PublicProfilesValue = {
    profiles: Record<string, PublicProfile>
    /** Asks for these users' profiles to be kept up to date; returns a function that stops asking */
    watch: (ids: string[]) => () => void
}

const PublicProfilesContext = createContext<PublicProfilesValue | null>(null)

/**
 * One shared cache of public profiles (name and avatar) for everything on screen. Components say which ids they show; the provider
 * fetches the union of them in one request series and refreshes it, instead of every component polling on its own.
 */
export const PublicProfilesProvider = ({ children }: { children: ReactNode }) => {
    const { user } = useCurrentUser()
    const [profiles, setProfiles] = useState<Record<string, PublicProfile>>({})
    const watchers = useRef(new Map<object, string[]>())
    // The ids anyone is watching, sorted and comma separated: a primitive, so the loading effect reruns only when the set changes
    const [wanted, setWanted] = useState("")

    const updateWanted = useCallback(() => {
        setWanted([...new Set([...watchers.current.values()].flat())].sort().join(","))
    }, [])

    const watch = useCallback((ids: string[]) => {
        const token = {}
        watchers.current.set(token, ids.filter((id) => UUID.test(id)))
        updateWanted()
        return () => {
            watchers.current.delete(token)
            updateWanted()
        }
    }, [updateWanted])

    useEffect(() => {
        if (!user || !wanted) return
        let active = true
        const ids = wanted.split(",")
        const chunks: string[][] = []
        for (let index = 0; index < ids.length; index += 100) chunks.push(ids.slice(index, index + 100))
        const load = () =>
            Promise.all(chunks.map((chunk) => {
                const params = new URLSearchParams()
                chunk.forEach((id) => params.append("ids", id))
                return gameApiParsed(user, `/api/users?${params}`, z.array(PublicProfileSchema))
            })).then((groups) => {
                if (active) setProfiles((current) => ({ ...current, ...Object.fromEntries(groups.flat().map((item) => [item.id, item])) }))
            }).catch(() => undefined)
        void load()
        const timer = setInterval(() => void load(), REFRESH_MS)
        return () => {
            active = false
            clearInterval(timer)
        }
    }, [user, wanted])

    const value = useMemo(() => ({ profiles, watch }), [profiles, watch])
    return <PublicProfilesContext value={value}>{children}</PublicProfilesContext>
}

/** The cached profiles, including those of the given ids once loaded */
export const usePublicProfiles = (ids: string[]) => {
    const context = useContext(PublicProfilesContext)
    if (!context) throw new Error("usePublicProfiles must be used within a PublicProfilesProvider")
    const { profiles, watch } = context
    const key = [...new Set(ids)].sort().join(",")
    useEffect(() => watch(key ? key.split(",") : []), [watch, key])
    return profiles
}
