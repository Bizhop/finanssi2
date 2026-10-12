import { z } from "zod/mini"

export const ChatMessageSchema = z.object({
    id: z.string(),
    userId: z.string(),
    message: z.string(),
    timestamp: z.number(),
})
export const ChatMessageSchemaArray = z.array(ChatMessageSchema)
export type ChatMessage = z.infer<typeof ChatMessageSchema>

export const NewChatMessageSchema = z.object({
    message: z.string().check(z.minLength(1), z.maxLength(100)),
})
export type NewChatMessage = z.infer<typeof NewChatMessageSchema>

export const PAGE_SIZE = 20

/** Union by id, newest first. Chat ids are fixed-width decimal strings, so lexical order matches database identity order. */
export const mergeMessages = (messages: ChatMessage[], moreMessages: ChatMessage[]) =>
    [...new Map([...messages, ...moreMessages].map((message) => [message.id, message])).values()]
        .sort((a, b) => a.id < b.id ? 1 : a.id > b.id ? -1 : 0)

/**
 * Applies the newest page, fetched after a (re)connect, to the loaded messages (newest first). When more messages were missed than fit
 * a page, the page has nothing in common with the loaded ones: it then replaces everything older than itself and loading older messages
 * continues from it (`hasOlder`); otherwise the page is merged in.
 */
export const applyNewestPage = (loaded: ChatMessage[], page: ChatMessage[]): { messages: ChatMessage[]; hasOlder: boolean | null } => {
    const newestKnown = loaded[0]
    const oldestInPage = page.at(-1)
    const missedMoreThanPage = newestKnown !== undefined && oldestInPage !== undefined && page.length === PAGE_SIZE && oldestInPage.id > newestKnown.id
    if (missedMoreThanPage) {
        return { messages: mergeMessages(page, loaded.filter((message) => message.id > oldestInPage.id)), hasOlder: true }
    }
    return { messages: mergeMessages(loaded, page), hasOlder: null }
}
