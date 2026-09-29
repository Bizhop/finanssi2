import { useEffect, useRef, useState } from "react"
import { Avatar, Box, Divider, IconButton, List, ListItem, ListItemAvatar, ListItemText, Paper, Stack, Tooltip, Typography } from "@mui/material"
import { User } from "firebase/auth"
import { z } from "zod/mini"
import { SubmitHandler, useForm } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import SendIcon from "@mui/icons-material/Send"

import { InputField } from "./FormInput.tsx"
import { useStompConnected, useStompSubscription } from "./StompContext.tsx"

const ChatMessageSchema = z.object({
    id: z.string(),
    username: z.string(),
    // Missing from messages saved before the backend stored it
    name: z.nullish(z.string()),
    message: z.string(),
    timestamp: z.number(),
    photoUrl: z.string(),
})

const ChatMessageSchemaArray = z.array(ChatMessageSchema)

type TChatMessage = z.infer<typeof ChatMessageSchema>

const NewChatMessageSchema = z.object({
    message: z.string().check(z.minLength(1), z.maxLength(100)),
})

type TNewChatMessage = z.infer<typeof NewChatMessageSchema>

type TChatProps = {
    user: User
}

const apiUrl = import.meta.env.VITE_FINANSSI_API_URL

const PAGE_SIZE = 20

// Union by id, newest first. Ids grow with creation time (MongoDB ObjectIds), and as equal-length hex strings they compare in the same order.
const mergeMessages = (messages: TChatMessage[], moreMessages: TChatMessage[]) =>
    [...new Map([...messages, ...moreMessages].map((message) => [message.id, message])).values()]
        .sort((a, b) => a.id < b.id ? 1 : a.id > b.id ? -1 : 0)

const Chat = ({ user }: TChatProps) => {
    // Newest first, like the backend returns them. The list is rendered with column-reverse, which shows them oldest at the top
    // and keeps the view anchored to the bottom (newest message) without any scrolling code.
    const [messages, setMessages] = useState<TChatMessage[]>([])
    const [hasOlderMessages, setHasOlderMessages] = useState(true)
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
        return user.getIdToken()
            .then((token) =>
                fetch(`${apiUrl}/api/chat?${params}`, {
                    method: "GET",
                    headers: {
                        "Authorization": `Bearer ${token}`,
                    },
                })
            )
            .then((response) => response.json())
            .then((response) => ChatMessageSchemaArray.parse(response))
    }

    // Loads the page before the oldest loaded message; with nothing loaded yet, the newest page
    const loadOlderMessages = () => {
        if (loadingOlderMessages.current || !hasOlderMessages) return
        loadingOlderMessages.current = true

        fetchPage(messages.at(-1)?.id)
            .then((page) => {
                // Cleared before the state updates, so the re-render can already trigger the next load
                loadingOlderMessages.current = false
                // Merged, as the same messages can also come from the websocket or from refreshNewestMessages
                setMessages((prevMessages) => mergeMessages(prevMessages, page))
                setHasOlderMessages(page.length === PAGE_SIZE)
            })
            .catch((error) => {
                // No automatic retry: the next attempt comes when the user scrolls or a message arrives
                loadingOlderMessages.current = false
                console.error(error)
            })
    }

    const sendChatMessage: SubmitHandler<TNewChatMessage> = (data) => {
        user.getIdToken().then((token) =>
            fetch(`${apiUrl}/api/chat`, {
                method: "POST",
                body: JSON.stringify(data),
                headers: {
                    "Authorization": `Bearer ${token}`,
                    "Content-Type": "application/json",
                },
            })
        )

        reset()
    }

    const receiveMessage = (jsonString: string) => {
        const parsedObject = JSON.parse(jsonString)
        const safeParsed = ChatMessageSchema.safeParse(parsedObject)

        if (safeParsed.success) {
            // A message posted while a page was loading can arrive both in the page and over the websocket
            setMessages((prevMessages) => mergeMessages(prevMessages, [safeParsed.data]))
        }
    }

    useStompSubscription("/topic/chat", receiveMessage)

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
            .catch(console.error)

    const connected = useStompConnected()
    useEffect(() => {
        if (connected) refreshNewestMessages()
    }, [connected])

    // Load older messages whenever the top of the list is in view: on mount (the first page), when the user scrolls up, and again after a page that didn't fill
    // the list. Re-created on every change so the callback sees the current messages; observe() reports the current state right away.
    const messageListRef = useRef<HTMLUListElement>(null)
    const loadMoreTriggerRef = useRef<HTMLLIElement>(null)
    useEffect(() => {
        const trigger = loadMoreTriggerRef.current
        if (!trigger || !hasOlderMessages) return
        const observer = new IntersectionObserver(
            ([entry]) => {
                if (entry.isIntersecting) loadOlderMessages()
            },
            // Start loading a bit before the user reaches the top
            { root: messageListRef.current, rootMargin: "200px 0px 0px 0px" },
        )
        observer.observe(trigger)
        return () => observer.disconnect()
    }, [messages, hasOlderMessages])

    return (
        <Stack direction="column" sx={{ flex: 1, minHeight: 0 }}>
            <h1>Chat</h1>
            <Box component={Paper} sx={{ flex: 1, minHeight: 0, display: "flex", flexDirection: "column" }}>
                <List
                    ref={messageListRef}
                    sx={{
                        flex: 1,
                        overflowY: "auto",
                        display: "flex",
                        flexDirection: "column-reverse",
                        bgcolor: "background.paper",
                    }}
                >
                    {messages.map((msg) => <ChatLine key={msg.id} message={msg} />)}
                    {/* Last in the DOM, so at the top of the reversed list */}
                    <li ref={loadMoreTriggerRef} aria-hidden />
                </List>
            </Box>
            {!connected && (
                <Typography variant="caption" color="warning" sx={{ mt: 1 }}>
                    Connecting to chat… New messages appear once connected.
                </Typography>
            )}
            <form onSubmit={handleSubmit(sendChatMessage)}>
                {/* useFlexGap: with margin-based spacing, Stack would reset the send button's top margin */}
                <Stack direction="row" spacing={1} useFlexGap sx={{ alignItems: "flex-start", mt: 2 }}>
                    <Box sx={{ flexGrow: 1 }}>
                        <InputField
                            control={control}
                            name="message"
                            label="Message"
                            type="text"
                            // An empty field only disables sending, it's not worth an error message
                            error={errors.message?.type === "too_small" ? undefined : errors.message}
                        />
                    </Box>
                    <IconButton
                        color="primary"
                        type="submit"
                        sx={{ mt: 1 }}
                        disabled={isSubmitting || !isDirty || !isValid}
                    >
                        <SendIcon />
                    </IconButton>
                </Stack>
            </form>
        </Stack>
    )
}

type ChatLineProps = {
    message: TChatMessage
}

const ChatLine = ({ message }: ChatLineProps) => {
    return (
        <>
            <ListItem>
                <ListItemAvatar>
                    <Tooltip title={message.username} placement="left">
                        <Avatar src={message.photoUrl} />
                    </Tooltip>
                </ListItemAvatar>
                <ListItemText
                    primary={
                        <Tooltip title={formatDate(message.timestamp)} placement="top-start">
                            <span>
                                <Box component="span" sx={{ fontWeight: "fontWeightMedium", color: "primary.main" }}>
                                    {senderFirstName(message)}:
                                </Box>{" "}
                                {message.message}
                            </span>
                        </Tooltip>
                    }
                    // Long words (e.g. links) wrap instead of widening the list
                    sx={{ overflowWrap: "anywhere" }}
                />
            </ListItem>
            <Divider variant="inset" component="li" />
        </>
    )
}

// First word of the display name, or the email's local part when there's no name
const senderFirstName = (message: TChatMessage) => message.name?.trim().split(/\s+/)[0] || message.username.split("@")[0]

const formatDate = (timestamp: number) => {
    const date = new Date(timestamp)
    return date.toLocaleString()
}

export default Chat
