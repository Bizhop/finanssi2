import { useEffect, useRef, useState } from "react"
import type { User } from "firebase/auth"

import { t } from "../i18n/index.ts"
import { gameApi, GameApiError, segment } from "../api/gameApi.ts"
import { useStompConnected, useStompSubscription } from "../realtime/StompContext.tsx"
import { applyNewestPage, type ChatMessage, ChatMessageSchema, ChatMessageSchemaArray, mergeMessages, type NewChatMessage, PAGE_SIZE } from "./chatHistory.ts"

const accessChanged = (reason: unknown) => reason instanceof GameApiError && (reason.status === 403 || reason.status === 404)

/**
 * The messages of the general chat or of one game's chat, newest first: loaded page by page, kept current over the websocket and
 * topped up after every reconnect.
 */
export const useChatHistory = (user: User, gameId?: string) => {
    const path = gameId ? `/api/games/${segment(gameId)}/chat` : "/api/chat"
    const topic = gameId ? `/topic/games/${segment(gameId)}/chat` : "/topic/chat"
    const [messages, setMessages] = useState<ChatMessage[]>([])
    const [hasOlderMessages, setHasOlderMessages] = useState(true)
    const [loadingOlder, setLoadingOlder] = useState(false)
    const [error, setError] = useState<string | null>(null)
    const loadingOlderMessages = useRef(false)
    // For async callbacks that need the messages at the time they complete
    const messagesRef = useRef(messages)
    useEffect(() => {
        messagesRef.current = messages
    }, [messages])

    // The newest messages, or with a message id, the ones before it
    const fetchPage = (before?: string) => {
        const params = new URLSearchParams({ size: String(PAGE_SIZE) })
        if (before) params.set("before", before)
        return gameApi<ChatMessage[]>(user, `${path}?${params}`).then((response) => ChatMessageSchemaArray.parse(response))
    }

    // Loads the page before the oldest loaded message; with nothing loaded yet, the newest page
    const loadOlderMessages = () => {
        if (loadingOlderMessages.current || !hasOlderMessages) return
        loadingOlderMessages.current = true
        setLoadingOlder(true)

        fetchPage(messages.at(-1)?.id)
            .then((page) => {
                // Cleared before the state updates, so the re-render can already trigger the next load
                loadingOlderMessages.current = false
                setLoadingOlder(false)
                // Merged, as the same messages can also come from the websocket or from refreshNewestMessages
                setMessages((previous) => mergeMessages(previous, page))
                setHasOlderMessages(page.length === PAGE_SIZE)
            })
            .catch((reason) => {
                // No automatic retry: the next attempt comes when the user scrolls or a message arrives
                loadingOlderMessages.current = false
                setLoadingOlder(false)
                if (accessChanged(reason)) setMessages([])
                setError(reason instanceof Error ? reason.message : t("chat.loadFailed"))
                console.error(reason)
            })
    }

    /** Posts a message; resolves to whether it was sent, so the caller clears its draft only then */
    const sendMessage = async (data: NewChatMessage) => {
        setError(null)
        try {
            const saved = await gameApi<ChatMessage | undefined>(user, path, { method: "POST", body: JSON.stringify(data) })
            if (saved) setMessages((current) => mergeMessages(current, [ChatMessageSchema.parse(saved)]))
            return true
        } catch (reason) {
            setError(
                accessChanged(reason) ? t("chat.accessChanged") : reason instanceof Error ? reason.message : t("chat.sendFailed"),
            )
            return false
        }
    }

    const receiveMessage = (jsonString: string) => {
        let parsedObject: unknown
        try {
            parsedObject = JSON.parse(jsonString)
        } catch {
            return // not a message this client understands; the next page load picks up whatever was missed
        }
        const safeParsed = ChatMessageSchema.safeParse(parsedObject)
        // A message posted while a page was loading can arrive both in the page and over the websocket
        if (safeParsed.success) setMessages((previous) => mergeMessages(previous, [safeParsed.data]))
    }

    useStompSubscription(topic, receiveMessage)

    // Messages sent while the websocket was down (or before it first connected) never arrive over it, so fetch the newest page after every connect
    const refreshNewestMessages = () =>
        fetchPage()
            .then((page) => {
                // Applied to the messages as they are when the update runs, as others may have arrived meanwhile
                setMessages((previous) => applyNewestPage(previous, page).messages)
                if (applyNewestPage(messagesRef.current, page).hasOlder) setHasOlderMessages(true)
            })
            .catch((reason) => {
                if (accessChanged(reason)) setMessages([])
                setError(reason instanceof Error ? reason.message : t("chat.loadFailed"))
                console.error(reason)
            })

    const connected = useStompConnected()
    useEffect(() => {
        if (connected) refreshNewestMessages()
    }, [connected])

    return { messages, hasOlderMessages, loadingOlder, error, connected, loadOlderMessages, sendMessage }
}
