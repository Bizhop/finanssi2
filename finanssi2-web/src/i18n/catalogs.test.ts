import { assert, assertEquals } from "@std/assert"
import { en } from "./en.ts"
import { fi } from "./fi.ts"

const placeholders = (message: string) => [...message.matchAll(/\{\{(\w+)\}\}/g)].map((match) => match[1]).sort()

Deno.test("both languages have the same keys", () => {
    assertEquals(Object.keys(en).sort(), Object.keys(fi).sort())
})

Deno.test("no message is empty, and both languages use the same placeholders", () => {
    for (const [key, message] of Object.entries(fi)) {
        const english = en[key as keyof typeof en]
        assert(message.trim() !== "" && english.trim() !== "", `${key} is empty`)
        assertEquals(placeholders(english), placeholders(message), `${key} has different placeholders`)
    }
})
