import { useEffect, useRef, useState } from "react"
import { Alert, Avatar, Box, Button, Divider, IconButton, List, ListItem, ListItemAvatar, ListItemText, Paper, Stack, Tooltip, Typography } from "@mui/material"
import { User } from "firebase/auth"
import { z } from "zod/mini"
import { SubmitHandler, useForm } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import SendIcon from "@mui/icons-material/Send"

import { InputField } from "./FormInput.tsx"
import { useStompConnected, useStompSubscription } from "./StompContext.tsx"
import { avatarSrc, gameApi, GameApiError } from "./gameApi.ts"
import { PublicProfile, usePublicProfiles } from "./CurrentUserContext.tsx"

const ChatMessageSchema = z.object({
    id: z.string(),
    userId: z.string(),
    message: z.string(),
    timestamp: z.number(),
})

const ChatMessageSchemaArray = z.array(ChatMessageSchema)

type TChatMessage = z.infer<typeof ChatMessageSchema>

const NewChatMessageSchema = z.object({
    message: z.string().check(z.minLength(1), z.maxLength(100)),
})

type TNewChatMessage = z.infer<typeof NewChatMessageSchema>

type TChatProps = {
    user: User
    gameId?: string
    embedded?: boolean
    compact?: boolean
    canSend?: boolean
    expanded?: boolean
}

const PAGE_SIZE = 20

// Union by id, newest first. Chat ids are fixed-width decimal strings, so lexical order matches database identity order.
const mergeMessages = (messages: TChatMessage[], moreMessages: TChatMessage[]) =>
    [...new Map([...messages, ...moreMessages].map((message) => [message.id, message])).values()]
        .sort((a, b) => a.id < b.id ? 1 : a.id > b.id ? -1 : 0)

const Chat = ({ user, gameId, embedded = false, compact = false, canSend = true, expanded = true }: TChatProps) => {
    const path = gameId ? `/api/games/${gameId}/chat` : "/api/chat"
    const topic = gameId ? `/topic/games/${gameId}/chat` : "/topic/chat"
    // Newest first, like the backend returns them. The list is rendered with column-reverse, which shows them oldest at the top
    // and keeps the view anchored to the bottom (newest message) without any scrolling code.
    const [messages, setMessages] = useState<TChatMessage[]>([])
    const profiles = usePublicProfiles(messages.map((message) => message.userId))
    const [hasOlderMessages, setHasOlderMessages] = useState(true)
    const [loadingOlder, setLoadingOlder] = useState(false)
    const [error, setError] = useState<string | null>(null)
    const loadingOlderMessages = useRef(false)
    // For async callbacks that need the messages at the time they complete
    const messagesRef = useRef(messages)
    messagesRef.current = messages
    const { control, handleSubmit, formState: { errors, isDirty, isSubmitting, isValid }, reset } = useForm<TNewChatMessage>({
        resolver: zodResolver(NewChatMessageSchema),
        mode: "all",
        defaultValues: { message: "" },
    })

    // The newest messages, or with a message id, the ones before it
    const fetchPage = (before?: string) => {
        const params = new URLSearchParams({ size: String(PAGE_SIZE) })
        if (before) params.set("before", before)
        return gameApi<TChatMessage[]>(user, `${path}?${params}`).then((response) => ChatMessageSchemaArray.parse(response))
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
                setMessages((prevMessages) => mergeMessages(prevMessages, page))
                setHasOlderMessages(page.length === PAGE_SIZE)
            })
            .catch((error) => {
                // No automatic retry: the next attempt comes when the user scrolls or a message arrives
                loadingOlderMessages.current = false
                setLoadingOlder(false)
                if (error instanceof GameApiError && (error.status === 403 || error.status === 404)) setMessages([])
                setError(error instanceof Error ? error.message : "Could not load chat history.")
                console.error(error)
            })
    }

    const sendChatMessage: SubmitHandler<TNewChatMessage> = async (data) => {
        setError(null)
        try {
            const saved = await gameApi<TChatMessage | undefined>(user, path, { method: "POST", body: JSON.stringify(data) })
            if (saved) setMessages((current) => mergeMessages(current, [ChatMessageSchema.parse(saved)]))
            reset()
        } catch (reason) {
            setError(
                reason instanceof GameApiError && (reason.status === 403 || reason.status === 404)
                    ? "Chat access changed. Return to the games list and reopen this game."
                    : reason instanceof Error
                    ? reason.message
                    : "Message could not be sent. Your draft is still here.",
            )
        }
    }

    const receiveMessage = (jsonString: string) => {
        const parsedObject = JSON.parse(jsonString)
        const safeParsed = ChatMessageSchema.safeParse(parsedObject)

        if (safeParsed.success) {
            // A message posted while a page was loading can arrive both in the page and over the websocket
            setMessages((prevMessages) => mergeMessages(prevMessages, [safeParsed.data]))
        }
    }

    useStompSubscription(topic, receiveMessage)

    useEffect(() => {
        setMessages([])
        setHasOlderMessages(true)
        loadingOlderMessages.current = false
        setLoadingOlder(false)
        setError(null)
        reset()
    }, [path, user.uid, reset])

    // Messages sent while the websocket was down (or before it first connected) never arrive over it, so fetch the newest page after every connect
    const refreshNewestMessages = () =>
        fetchPage()
            .then((page) => {
                const newestKnown = messagesRef.current[0]
                // Nothing in common with the loaded messages: more were missed than fit a page, so continue loading older ones from this page instead
                const missedMoreThanPage = newestKnown !== undefined && page.length === PAGE_SIZE && page.at(-1)!.id > newestKnown.id
                if (missedMoreThanPage) {
                    const oldestInPage = page.at(-1)!.id
                    setMessages((prevMessages) => mergeMessages(page, prevMessages.filter((message) => message.id > oldestInPage)))
                    setHasOlderMessages(true)
                } else {
                    setMessages((prevMessages) => mergeMessages(prevMessages, page))
                }
            })
            .catch((reason) => {
                if (reason instanceof GameApiError && (reason.status === 403 || reason.status === 404)) setMessages([])
                setError(reason instanceof Error ? reason.message : "Could not load chat history.")
                console.error(reason)
            })

    const connected = useStompConnected()
    useEffect(() => {
        if (connected) refreshNewestMessages()
    }, [connected])

    // Load history as the top sentinel enters the scroll area. The compact list only loads while it overflows,
    // so a tall panel does not fetch the entire history just because the sentinel is initially visible.
    const messageListRef = useRef<HTMLUListElement>(null)
    const loadMoreTriggerRef = useRef<HTMLLIElement>(null)
    useEffect(() => {
        const trigger = loadMoreTriggerRef.current
        if (!trigger || !hasOlderMessages || !expanded) return
        const observer = new IntersectionObserver(
            ([entry]) => {
                const list = messageListRef.current
                if (entry.isIntersecting && list && list.scrollHeight > list.clientHeight + 1) loadOlderMessages()
            },
            // Start loading a bit before the user reaches the top
            { root: messageListRef.current, rootMargin: "200px 0px 0px 0px" },
        )
        observer.observe(trigger)
        return () => observer.disconnect()
    }, [messages, hasOlderMessages, expanded, compact])

    return (
        <Stack direction="column" sx={{ flex: 1, minHeight: 0 }}>
            {!embedded && <h1>Chat</h1>}
            <Box component={embedded ? "div" : Paper} sx={{ flex: 1, minHeight: 0, display: "flex", flexDirection: "column" }}>
                <List
                    ref={messageListRef}
                    dense={compact}
                    sx={{
                        flex: 1,
                        overflowY: "auto",
                        display: "flex",
                        flexDirection: "column-reverse",
                        bgcolor: "background.paper",
                        ...(compact && {
                            fontSize: "0.875rem",
                            "& .MuiListItem-root": { py: 0.25, px: 0.5 },
                            "& .MuiListItemAvatar-root": { minWidth: 38 },
                            "& .MuiListItemText-primary": { fontSize: "inherit", lineHeight: 1.3 },
                        }),
                    }}
                >
                    {messages.map((msg) => <ChatLine key={msg.id} message={msg} compact={compact} profile={profiles[msg.userId]} />)}
                    {/* Last in the DOM, so at the top of the reversed list */}
                    {hasOlderMessages && (
                        <ListItem ref={loadMoreTriggerRef} component="li" disablePadding sx={{ justifyContent: "center", py: 0.5 }}>
                            {compact
                                ? (
                                    <Typography variant="caption" color="text.secondary">
                                        {loadingOlder ? "Loading older messages…" : "Scroll up to load older messages"}
                                    </Typography>
                                )
                                : (
                                    <Button size="small" disabled={loadingOlder} onClick={loadOlderMessages}>
                                        {loadingOlder ? "Loading older messages…" : "Load older messages"}
                                    </Button>
                                )}
                        </ListItem>
                    )}
                </List>
            </Box>
            {!connected && (
                <Typography variant="caption" color="warning" sx={{ mt: 1 }}>
                    Connecting to chat… New messages appear once connected.
                </Typography>
            )}
            {error && <Alert severity="error" sx={{ mt: 1 }}>{error}</Alert>}
            {canSend
                ? (
                    <form onSubmit={handleSubmit(sendChatMessage)}>
                        {/* useFlexGap: with margin-based spacing, Stack would reset the send button's top margin */}
                        <Stack direction="row" spacing={1} useFlexGap sx={{ alignItems: "flex-start", mt: compact ? 1 : 2 }}>
                            <Box sx={{ flexGrow: 1 }}>
                                <InputField
                                    control={control}
                                    name="message"
                                    label="Message"
                                    type="text"
                                    size={compact ? "small" : "medium"}
                                    // An empty field only disables sending, it's not worth an error message
                                    error={errors.message?.type === "too_small" ? undefined : errors.message}
                                />
                            </Box>
                            <IconButton
                                color="primary"
                                type="submit"
                                size={compact ? "small" : "medium"}
                                sx={{ mt: compact ? 0.5 : 1 }}
                                disabled={isSubmitting || !isDirty || !isValid}
                            >
                                <SendIcon />
                            </IconButton>
                        </Stack>
                    </form>
                )
                : <Typography variant="caption" sx={{ mt: 1 }}>Join this game to send messages.</Typography>}
        </Stack>
    )
}

type ChatLineProps = {
    message: TChatMessage
    compact: boolean
    profile?: PublicProfile
}

const ChatLine = ({ message, compact, profile }: ChatLineProps) => {
    return (
        <>
            <ListItem>
                <ListItemAvatar>
                    <Tooltip title={message.userId} placement="left">
                        <Avatar src={avatarSrc(profile?.avatar)} alt={profile?.displayName ?? "Player"} sx={compact ? { width: 30, height: 30 } : undefined}>
                            {(profile?.displayName ?? "?").slice(0, 1).toUpperCase()}
                        </Avatar>
                    </Tooltip>
                </ListItemAvatar>
                <ListItemText
                    primary={
                        <Tooltip title={formatDate(message.timestamp)} placement="top-start">
                            <span>
                                <Box component="span" sx={{ fontWeight: "fontWeightMedium", color: "primary.main" }}>
                                    {senderFirstName(profile?.displayName)}:
                                </Box>{" "}
                                {message.message}
                            </span>
                        </Tooltip>
                    }
                    // Long words (e.g. links) wrap instead of widening the list
                    sx={{ overflowWrap: "anywhere" }}
                />
            </ListItem>
            <Divider variant={compact ? "fullWidth" : "inset"} component="li" />
        </>
    )
}

// First word of the display name, or the email's local part when there's no name
const senderFirstName = (displayName?: string) => displayName?.trim().split(/\s+/)[0] || "Player"

const formatDate = (timestamp: number) => {
    const date = new Date(timestamp)
    return date.toLocaleString()
}

export default Chat
