import React, { useEffect, useState } from "react"
import { Avatar, Box, Divider, IconButton, List, ListItem, ListItemAvatar, ListItemText, Paper, Stack, Tooltip } from "@mui/material"
import { User } from "firebase/auth"
import { useSubscription } from "react-stomp-hooks"
import { z } from "zod"
import { SubmitHandler, useForm } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import SendIcon from "@mui/icons-material/Send"

import { InputField } from "./FormInput"

const ChatMessageSchema = z.object({
    username: z.string(),
    message: z.string(),
    timestamp: z.number(),
    photoUrl: z.string()
})

const ChatMessageSchemaArray = z.array(ChatMessageSchema)

type TChatMessage = z.infer<typeof ChatMessageSchema>

const NewChatMessageSchema = z.object({
    message: z.string().min(1).max(100)
})

type TNewChatMessage = z.infer<typeof NewChatMessageSchema>

type TChatProps = {
    user: User
}

const apiUrl = import.meta.env.VITE_FINANSSI_API_URL

const Chat = ({ user }: TChatProps) => {
    const [messages, setMessages] = useState<TChatMessage[]>([])
    const { control, handleSubmit, formState: { errors, isDirty, isSubmitting, isValid }, reset } = useForm<TNewChatMessage>(
        {
            resolver: zodResolver(NewChatMessageSchema),
            mode: "all",
            defaultValues: { message: "" }
        })

    useEffect(() => {
        user.getIdToken().then(token =>
            fetch(`${apiUrl}/api/chat`, {
                method: "GET",
                headers: {
                    "Authorization": `Bearer ${token}`
                }
            })
                .then(response => response.json())
                .then(response => {
                    const safeParsed = ChatMessageSchemaArray.parse(response)
                    setMessages(safeParsed)
                })
                .catch(console.error)
        )
    }, [])

    const sendChatMessage: SubmitHandler<TNewChatMessage> = (data) => {
        user.getIdToken().then(token =>
            fetch(`${apiUrl}/api/chat`, {
                method: "POST",
                body: JSON.stringify(data),
                headers: {
                    "Authorization": `Bearer ${token}`,
                    "Content-Type": "application/json"
                }
            })
        )

        reset()
    }

    const receiveMessage = (jsonString: string) => {
        const parsedObject = JSON.parse(jsonString)
        const safeParsed = ChatMessageSchema.safeParse(parsedObject)

        if (safeParsed.success) {
            setMessages(prevMessages => {
                const addedToMessageArray = [...prevMessages, safeParsed.data]
                return addedToMessageArray.length > 10 ? addedToMessageArray.slice(-10) : addedToMessageArray
            })
        }
    }

    useSubscription("/topic/chat", message => receiveMessage(message.body))

    return <Stack direction="column">
        <h1>Chat</h1>
        <Box component={Paper}>
            <List sx={{ width: "100%", maxWidth: 360, bgcolor: "background.paper" }}>
                {messages.map((msg, index) => <ChatLine key={`chat-message-${index}`} message={msg} />)}
            </List>
            <form onSubmit={handleSubmit(sendChatMessage)}>
                <Stack direction="row">
                    <InputField control={control} name="message" label="Message" type="text" error={errors.message} />
                    <IconButton color="primary" type="submit" disabled={isSubmitting || !isDirty || !isValid}>
                        <SendIcon />
                    </IconButton>
                </Stack>
            </form>
        </Box>
    </Stack>
}

type ChatLineProps = {
    message: TChatMessage
}

const ChatLine = ({ message }: ChatLineProps) => {
    return <>
        <ListItem alignItems="flex-start">
            <ListItemAvatar>
                <Tooltip title={message.username} placement="left">
                    <Avatar src={message.photoUrl} />
                </Tooltip>
            </ListItemAvatar>
            <ListItemText primary={message.message} secondary={formatDate(message.timestamp)} />
        </ListItem>
        <Divider variant="inset" component="li" />
    </>
}

const formatDate = (timestamp: number) => {
    const date = new Date(timestamp)
    return date.toLocaleString()
}

export default Chat
