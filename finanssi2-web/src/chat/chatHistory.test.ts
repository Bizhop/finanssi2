import { assertEquals } from "@std/assert"
import { applyNewestPage, type ChatMessage, mergeMessages, PAGE_SIZE } from "./chatHistory.ts"

const message = (n: number): ChatMessage => ({ id: String(n).padStart(19, "0"), userId: "u", message: `m${n}`, timestamp: n })
const ids = (messages: ChatMessage[]) => messages.map((item) => Number(item.id))
/** Newest first, like the backend returns pages */
const range = (from: number, to: number) => Array.from({ length: to - from + 1 }, (_, i) => message(to - i))

Deno.test("merging keeps one copy of each message, newest first", () => {
    assertEquals(ids(mergeMessages(range(3, 5), range(1, 3))), [5, 4, 3, 2, 1])
    assertEquals(ids(mergeMessages([], range(1, 2))), [2, 1])
})

Deno.test("a newest page that overlaps what is loaded is merged in", () => {
    const loaded = range(1, 30)
    const page = range(21, 40)
    const result = applyNewestPage(loaded, page)
    assertEquals(ids(result.messages), range(1, 40).map((item) => Number(item.id)))
    assertEquals(result.hasOlder, null)
})

Deno.test("a short newest page is merged even when everything before it was missed", () => {
    const result = applyNewestPage(range(1, 5), range(6, 10))
    assertEquals(ids(result.messages).length, 10)
    assertEquals(result.hasOlder, null)
})

Deno.test("missing more than a page replaces the older loaded messages and resumes loading from the page", () => {
    const loaded = range(1, 10)
    const page = range(100, 100 + PAGE_SIZE - 1)
    const result = applyNewestPage(loaded, page)
    assertEquals(ids(result.messages), ids(page))
    assertEquals(result.hasOlder, true)
})

Deno.test("with nothing loaded the page just becomes the messages", () => {
    const result = applyNewestPage([], range(1, PAGE_SIZE))
    assertEquals(result.messages.length, PAGE_SIZE)
    assertEquals(result.hasOlder, null)
})
