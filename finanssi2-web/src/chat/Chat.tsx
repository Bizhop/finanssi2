import { useEffect, useEffectEvent, useRef } from "react"
import { Alert, Avatar, Box, Button, Divider, IconButton, List, ListItem, ListItemAvatar, ListItemText, Paper, Stack, Tooltip, Typography } from "@mui/material"
import type { User } from "firebase/auth"
import { type SubmitHandler, useForm } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import SendIcon from "@mui/icons-material/Send"
import { useTranslation } from "react-i18next"

import { InputField } from "../ui/InputField.tsx"
import { avatarSrc, type PublicProfile } from "../api/gameApi.ts"
import { usePublicProfiles } from "../auth/PublicProfiles.tsx"
import { formatDateTime } from "../ui/format.ts"
import { type ChatMessage, type NewChatMessage, NewChatMessageSchema } from "./chatHistory.ts"
import { useChatHistory } from "./useChatHistory.ts"

type ChatProps = {
    user: User
    gameId?: string
    embedded?: boolean
    compact?: boolean
    canSend?: boolean
    expanded?: boolean
}

export const Chat = ({ user, gameId, embedded = false, compact = false, canSend = true, expanded = true }: ChatProps) => {
    const { t } = useTranslation()
    const { messages, hasOlderMessages, loadingOlder, error, connected, loadOlderMessages, sendMessage } = useChatHistory(user, gameId)
    const profiles = usePublicProfiles(messages.map((message) => message.userId))
    const { control, handleSubmit, formState: { errors, isDirty, isSubmitting, isValid }, reset } = useForm<NewChatMessage>({
        resolver: zodResolver(NewChatMessageSchema),
        mode: "all",
        defaultValues: { message: "" },
    })
    const sendChatMessage: SubmitHandler<NewChatMessage> = async (data) => {
        if (await sendMessage(data)) reset()
    }

    // Load history as the top sentinel enters the scroll area. The compact list only loads while it overflows,
    // so a tall panel does not fetch the entire history just because the sentinel is initially visible.
    const messageListRef = useRef<HTMLUListElement>(null)
    const loadMoreTriggerRef = useRef<HTMLLIElement>(null)
    const loadOlder = useEffectEvent(() => loadOlderMessages())
    useEffect(() => {
        const trigger = loadMoreTriggerRef.current
        if (!trigger || !hasOlderMessages || !expanded) return
        const observer = new IntersectionObserver(
            ([entry]) => {
                const list = messageListRef.current
                if (entry.isIntersecting && list && list.scrollHeight > list.clientHeight + 1) loadOlder()
            },
            // Start loading a bit before the user reaches the top
            { root: messageListRef.current, rootMargin: "200px 0px 0px 0px" },
        )
        observer.observe(trigger)
        return () => observer.disconnect()
    }, [messages, hasOlderMessages, expanded, compact])

    return (
        <Stack direction="column" spacing={2} sx={{ flex: 1, minHeight: 0, ...(!embedded && { p: 2 }) }}>
            {!embedded && <Typography variant="h5" component="h1">{t("chat.title")}</Typography>}
            <Box
                component={embedded ? "div" : Paper}
                sx={{
                    flex: 1,
                    minHeight: 0,
                    display: "flex",
                    flexDirection: "column",
                    ...(!embedded && { border: 1, borderColor: "divider", borderRadius: 1, p: 2, boxShadow: "none" }),
                }}
            >
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
                                        {loadingOlder ? t("chat.loadingOlder") : t("chat.scrollUp")}
                                    </Typography>
                                )
                                : (
                                    <Button size="small" disabled={loadingOlder} onClick={loadOlderMessages}>
                                        {loadingOlder ? t("chat.loadingOlder") : t("chat.loadOlder")}
                                    </Button>
                                )}
                        </ListItem>
                    )}
                </List>
                {!connected && (
                    <Typography variant="caption" color="warning" sx={{ mt: 1 }}>
                        {t("chat.connecting")}
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
                                        label={t("chat.message")}
                                        type="text"
                                        size={compact ? "small" : "medium"}
                                        // An empty field only disables sending, it's not worth an error message
                                        error={errors.message?.type === "too_small" ? undefined : errors.message}
                                    />
                                </Box>
                                <IconButton
                                    color="primary"
                                    type="submit"
                                    aria-label={t("chat.send")}
                                    size={compact ? "small" : "medium"}
                                    sx={{ mt: compact ? 0.5 : 1 }}
                                    disabled={isSubmitting || !isDirty || !isValid}
                                >
                                    <SendIcon />
                                </IconButton>
                            </Stack>
                        </form>
                    )
                    : <Typography variant="caption" sx={{ mt: 1 }}>{t("chat.joinToSend")}</Typography>}
            </Box>
        </Stack>
    )
}

type ChatLineProps = {
    message: ChatMessage
    compact: boolean
    profile?: PublicProfile
}

const ChatLine = ({ message, compact, profile }: ChatLineProps) => {
    const { t } = useTranslation()
    return (
        <>
            <ListItem>
                <ListItemAvatar>
                    <Avatar
                        src={avatarSrc(profile?.avatar)}
                        alt={profile?.displayName ?? t("common.player")}
                        sx={compact ? { width: 30, height: 30 } : undefined}
                    >
                        {(profile?.displayName ?? "?").slice(0, 1).toUpperCase()}
                    </Avatar>
                </ListItemAvatar>
                <ListItemText
                    primary={
                        <Tooltip title={formatDateTime(message.timestamp)} placement="top-start">
                            <span>
                                <Box component="span" sx={{ fontWeight: "fontWeightMedium", color: "primary.main" }}>
                                    {senderFirstName(profile?.displayName) ?? t("common.player")}:
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
const senderFirstName = (displayName?: string) => displayName?.trim().split(/\s+/)[0] || undefined
