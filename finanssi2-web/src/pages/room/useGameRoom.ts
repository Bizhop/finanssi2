import { useCallback, useEffect, useEffectEvent, useRef, useState } from "react"
import { useNavigate } from "react-router"

import { gameApi, GameApiError, gameApiParsed, type GameBoardData, type GameView, segment } from "../../api/gameApi.ts"
import { GameBoardDataSchema, GameViewSchema } from "../../api/schemas.ts"
import { useCurrentUser } from "../../auth/CurrentUserContext.tsx"
import type { GameLogEntry } from "../../game/labels/gameEvents.ts"
import { useStompConnected, useStompSubscription } from "../../realtime/StompContext.tsx"
import { errorToast } from "../../ui/toasts.ts"
import { hasEventGap, mergeEvents } from "./eventLog.ts"

type GameUpdate = { version: number; events: GameLogEntry[]; deleted?: boolean }

export const apiError = (reason: unknown) => reason instanceof Error ? reason.message : "The request failed"

/**
 * One game as the player sees it: the view, the board data and the event log, loaded on mount and after every reconnect and kept
 * current by the game's websocket topic, and the ways to send commands. The room remounts this per game and account.
 */
export const useGameRoom = (id: string | undefined) => {
    const { user, status, debugMode, clearDebugAccess } = useCurrentUser()
    const navigate = useNavigate()
    // Late responses after unmount must not toast or navigate
    const mounted = useRef(true)
    const refreshSequence = useRef(0)
    const busyRef = useRef(false)
    const connected = useStompConnected()
    const [view, setView] = useState<GameView | null>(null)
    const [board, setBoard] = useState<GameBoardData | null>(null)
    const [events, setEvents] = useState<GameLogEntry[]>([])
    const [error, setError] = useState<string | null>(null)
    const [commandBusy, setCommandBusy] = useState(false)
    const [diceInput, setDiceInput] = useState("")

    const leaveRoom = useCallback(() => {
        setView(null)
        navigate("/games", { replace: true })
    }, [navigate])

    const refresh = useCallback(async () => {
        if (!user || !id) return
        const sequence = ++refreshSequence.current
        try {
            const latest = await gameApiParsed(user, `/api/games/${segment(id)}`, GameViewSchema)
            if (!mounted.current || sequence !== refreshSequence.current) return
            setView(latest)
            setError(null)
        } catch (reason) {
            if (!mounted.current || sequence !== refreshSequence.current) return
            if (reason instanceof GameApiError && (reason.status === 403 || reason.status === 404)) {
                if (reason.status === 403) clearDebugAccess()
                leaveRoom()
            }
            setError(apiError(reason))
        }
    }, [id, user, clearDebugAccess, leaveRoom])

    const refreshEvents = useCallback(async (after = 0) => {
        if (!user || !id) return
        try {
            const latest = await gameApi<GameLogEntry[]>(user, `/api/games/${segment(id)}/events?after=${after}`)
            if (!mounted.current) return
            setEvents((current) => mergeEvents(current, latest))
        } catch (reason) {
            if (mounted.current) errorToast(apiError(reason))
        }
    }, [id, user])

    useEffect(() => {
        mounted.current = true
        return () => {
            mounted.current = false
        }
    }, [])

    useEffect(() => {
        if (!user) return
        void gameApiParsed(user, "/api/game-data", GameBoardDataSchema).then((data) => {
            if (mounted.current) setBoard(data)
        }).catch((reason) => {
            if (mounted.current) setError(apiError(reason))
        })
    }, [user])

    const receiveUpdate = useCallback((body: string) => {
        if (!mounted.current) return
        try {
            const update = JSON.parse(body) as GameUpdate
            if (update.deleted) {
                navigate("/games", { replace: true })
                return
            }
            const currentSequence = events.at(-1)?.seq ?? 0
            if (hasEventGap(currentSequence, update.events)) void refreshEvents(currentSequence)
            else setEvents((current) => mergeEvents(current, update.events))
            // State is not included in broadcasts, so reload it after every event update.
            void refresh()
        } catch (reason) {
            console.error("Invalid game update", reason)
            void refresh()
            void refreshEvents(events.at(-1)?.seq ?? 0)
        }
    }, [events, refresh, refreshEvents, navigate])

    useStompSubscription(`/topic/games/${id}`, receiveUpdate)

    // Load on mount and again whenever the websocket (re)connects: updates sent while it was down never arrive over it
    const reload = useEffectEvent(() => {
        void refresh()
        void refreshEvents(events.at(-1)?.seq ?? 0)
    })
    useEffect(() => {
        reload()
    }, [connected, user])

    // Debug access that ends while a debug game is open closes the room
    useEffect(() => {
        if (view?.game.mode === "DEBUG" && status === "ready" && !debugMode) leaveRoom()
    }, [view, debugMode, status, leaveRoom])

    /** Runs an action, then reloads the game unless the action leaves it (`reload` false) */
    const mutate = async (action: () => Promise<unknown>, reload = true) => {
        if (busyRef.current) return
        busyRef.current = true
        setCommandBusy(true)
        try {
            await action()
            if (!mounted.current || !reload) return
            await refresh()
            await refreshEvents(events.at(-1)?.seq ?? 0)
        } catch (reason) {
            if (!mounted.current) return
            errorToast(apiError(reason))
            if (reason instanceof GameApiError && reason.status === 403 && view?.game.mode === "DEBUG") {
                clearDebugAccess()
                leaveRoom()
            } else {
                // A conflict reloads the view; the user must submit a new action for its actor/version.
                await refresh()
            }
        } finally {
            busyRef.current = false
            setCommandBusy(false)
        }
    }

    const sendCommand = async (command: Record<string, unknown>) => {
        if (!user || !id || !view || busyRef.current) return
        const debug = view.game.mode === "DEBUG"
        if (debug && !debugMode) return
        const dice = diceInput.trim() ? diceInput.trim().split(/[\s,]+/).map(Number) : undefined
        if (debug && dice && (dice.length > 32 || dice.some((value) => !Number.isInteger(value) || value < 1 || value > 6))) {
            errorToast("Dice must contain at most 32 values from 1 to 6")
            return
        }
        await mutate(async () => {
            await gameApi(user, debug ? `/api/debug/games/${segment(id)}/commands` : `/api/games/${segment(id)}/commands`, {
                method: "POST",
                body: JSON.stringify(debug ? { actor: view.actingPlayer, expectedVersion: view.game.version, command, dice } : command),
            })
            // Cleared only once used, so a rejected command can be retried with the same dice
            setDiceInput("")
        })
    }

    /** Debug games only: the next card of a deck */
    const selectCard = (deck: string, card: string) => {
        if (!user || !id || !view || !debugMode || !card) return
        void mutate(() =>
            gameApi(user, `/api/debug/games/${segment(id)}/next-card`, {
                method: "PUT",
                body: JSON.stringify({ deck, card, expectedVersion: view.game.version }),
            })
        )
    }

    return { user, view, board, events, error, commandBusy, diceInput, setDiceInput, sendCommand, selectCard }
}
