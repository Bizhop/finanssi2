import { useCallback, useEffect, useEffectEvent, useRef, useState } from "react"
import { useNavigate } from "react-router"

import { type Game, gameApi, GameApiError, gameApiParsed, segment } from "../../api/gameApi.ts"
import { GameSchema } from "../../api/schemas.ts"
import { z } from "zod/mini"
import { useCurrentUser } from "../../auth/CurrentUserContext.tsx"
import { useStompConnected, useStompSubscription } from "../../realtime/StompContext.tsx"
import { t } from "../../i18n/index.ts"
import { errorToast } from "../../ui/toasts.ts"

/** The house rules a game is created or changed with, as the backend takes them */
export type RuleSettingsValues = { loanLimit: string; compulsorySaleMinimumBid: string; shareholdersMeeting: string }

/**
 * The games list and the actions on it. Every action runs one at a time and reloads the list; each resolves to whether it succeeded, so
 * dialogs close only on success. `onForbidden` is called when the backend refuses with 403, which means debug access is gone.
 */
export const useGames = (onForbidden: () => void) => {
    const { user, refreshProfile, clearDebugAccess } = useCurrentUser()
    const userRef = useRef(user)
    // For async callbacks that must ignore results once the user has changed
    useEffect(() => {
        userRef.current = user
    }, [user])
    const requestRef = useRef(0)
    const busyRef = useRef(false)
    const connected = useStompConnected()
    const navigate = useNavigate()
    const [games, setGames] = useState<Game[]>([])
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState<string | null>(null)
    const [busyGame, setBusyGame] = useState<string | null>(null)

    const refresh = useCallback(() => {
        if (!user) return Promise.resolve()
        const request = ++requestRef.current
        return gameApiParsed(user, "/api/games", z.array(GameSchema))
            .then((items) => {
                if (userRef.current !== user || request !== requestRef.current) return
                setGames(items)
                setError(null)
            })
            .catch((reason: unknown) => {
                if (userRef.current === user && request === requestRef.current) setError(reason instanceof Error ? reason.message : t("games.loadFailed"))
            })
            .finally(() => {
                if (userRef.current === user && request === requestRef.current) setLoading(false)
            })
    }, [user])

    // Load on mount, and again on every (re)connect: list changes sent while the websocket was down never arrive over it. The app remounts
    // this per account, so no state needs resetting when the user changes.
    const load = useEffectEvent(() => {
        void refresh()
        if (connected) void refreshProfile()
    })
    useEffect(() => {
        load()
    }, [connected, user])

    useStompSubscription("/topic/games", () => void refresh())

    const run = async (id: string | null, action: () => Promise<unknown>, then?: () => void): Promise<boolean> => {
        if (busyRef.current) return false
        busyRef.current = true
        setBusyGame(id ?? "new")
        try {
            await action()
            if (userRef.current !== user) return false
            then?.()
            await refresh()
            return true
        } catch (reason) {
            if (reason instanceof GameApiError && reason.status === 403) {
                clearDebugAccess()
                onForbidden()
            }
            errorToast(reason instanceof Error ? reason.message : t("games.requestFailed"))
            return false
        } finally {
            busyRef.current = false
            setBusyGame(null)
        }
    }

    const post = (path: string) => user ? gameApi(user, path, { method: "POST" }) : Promise.resolve()
    const open = (game: Game) => () => navigate(`/games/${segment(game.id)}`)

    return {
        games,
        loading,
        error,
        busy: busyGame !== null,
        refresh,
        createGame: () => run(null, () => post("/api/games")),
        createDebugGame: (playerCount: number, settings: RuleSettingsValues) =>
            run(
                null,
                () => user ? gameApi<Game>(user, "/api/debug/games", { method: "POST", body: JSON.stringify({ playerCount, settings }) }) : Promise.resolve(),
            ),
        joinGame: (game: Game) => run(game.id, () => post(`/api/games/${segment(game.id)}/join`), open(game)),
        leaveGame: (game: Game) => run(game.id, () => post(`/api/games/${segment(game.id)}/leave`)),
        startGame: (game: Game) => run(game.id, () => post(`/api/games/${segment(game.id)}/start`), open(game)),
        closeGame: (game: Game, action: "end" | "delete") =>
            run(game.id, () => {
                if (!user) return Promise.resolve()
                return action === "delete"
                    ? gameApi<void>(user, `/api/debug/games/${segment(game.id)}`, { method: "DELETE" })
                    : gameApi(user, `/api/games/${segment(game.id)}/commands`, { method: "POST", body: JSON.stringify({ type: "EndGame" }) })
            }),
        saveSettings: (game: Game, settings: RuleSettingsValues) =>
            run(
                game.id,
                () => user ? gameApi(user, `/api/games/${segment(game.id)}/settings`, { method: "PUT", body: JSON.stringify(settings) }) : Promise.resolve(),
            ),
        open,
    }
}
