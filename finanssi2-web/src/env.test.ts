import { assertEquals, assertThrows } from "@std/assert"
import { parseEnv } from "./env.ts"

const valid = {
    VITE_FIREBASE_API_KEY: "key",
    VITE_FIREBASE_AUTH_DOMAIN: "example.firebaseapp.com",
    VITE_FIREBASE_PROJECT_ID: "project",
    VITE_FIREBASE_STORAGE_BUCKET: "bucket",
    VITE_FIREBASE_MESSAGING_SENDER_ID: "1",
    VITE_FIREBASE_APP_ID: "app",
    VITE_FINANSSI_API_URL: "https://api.example.com/",
}

Deno.test("the API URL loses its trailing slash and gives the websocket URL", () => {
    const env = parseEnv(valid)
    assertEquals(env.apiUrl, "https://api.example.com")
    assertEquals(env.stompUrl, "wss://api.example.com/ws")
    assertEquals(parseEnv({ ...valid, VITE_FINANSSI_API_URL: "http://localhost:8080" }).stompUrl, "ws://localhost:8080/ws")
})

Deno.test("a missing or invalid setting is named in the error", () => {
    const { VITE_FIREBASE_APP_ID: _omitted, ...missing } = valid
    assertThrows(() => parseEnv(missing), Error, "VITE_FIREBASE_APP_ID")
    assertThrows(() => parseEnv({ ...valid, VITE_FINANSSI_API_URL: "not a url" }), Error, "VITE_FINANSSI_API_URL")
})
