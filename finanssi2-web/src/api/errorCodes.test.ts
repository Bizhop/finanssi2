import { assertEquals, assertInstanceOf } from "@std/assert"
import type { User } from "firebase/auth"
import { en } from "../i18n/en.ts"
import { fi } from "../i18n/fi.ts"
import { initI18n, setLanguage } from "../i18n/index.ts"
import { gameApi, GameApiError } from "./gameApi.ts"

const PREFIX = "apiError."
const user = { getIdToken: () => Promise.resolve("token") } as User

/** The constants of the backend's ErrorCode enum, read from its source so the two cannot drift apart */
const backendCodes = async () => {
    const source = await Deno.readTextFile(new URL("../../../finanssi2-backend/src/main/java/fi/bizhop/finanssi2/error/ErrorCode.java", import.meta.url))
    const body = source.slice(source.indexOf("{", source.indexOf("enum ErrorCode")) + 1, source.lastIndexOf("}")).replace(/\/\/.*$/gm, "")
    return body.split(",").map((name) => name.trim()).filter(Boolean)
}

Deno.test("every backend error code has wording in both languages, and nothing else does", async () => {
    const codes = (await backendCodes()).sort()
    for (const [language, catalog] of [["fi", fi], ["en", en]] as const) {
        const keys = Object.keys(catalog).filter((key) => key.startsWith(PREFIX)).map((key) => key.slice(PREFIX.length)).sort()
        assertEquals(keys, codes, `${language} wording does not match ErrorCode.java`)
    }
})

const failWith = async (body: unknown, status = 409) => {
    const originalFetch = globalThis.fetch
    globalThis.fetch = () => Promise.resolve(Response.json(body, { status }))
    try {
        return await gameApi(user, "/api/x").then(() => undefined, (reason: unknown) => reason)
    } finally {
        globalThis.fetch = originalFetch
    }
}

Deno.test("an error with a known code is worded in the UI language, with its parameters", async () => {
    initI18n("fi")
    const finnish = await failWith({ code: "NO_LOANS_ON_SQUARE", params: { square: 21 }, detail: "No new loans on square 21" })
    assertInstanceOf(finnish, GameApiError)
    assertEquals([finnish.message, finnish.code, finnish.params], ["Uutta lainaa ei voi ottaa ruudussa 21.", "NO_LOANS_ON_SQUARE", { square: 21 }])

    await setLanguage("en")
    const english = await failWith({ code: "NO_LOANS_ON_SQUARE", params: { square: 21 }, detail: "Backend wording" })
    assertEquals((english as GameApiError).message, "No new loans can be taken on square 21.")
    await setLanguage("fi")
})

Deno.test("an unknown code or none falls back to the backend's detail", async () => {
    initI18n("fi")
    assertEquals(((await failWith({ code: "SOME_NEW_CODE", detail: "Brand new rule" })) as GameApiError).message, "Brand new rule")
    assertEquals(((await failWith({ detail: "No code here" })) as GameApiError).message, "No code here")
})
