// The build-time settings, parsed once with a clear message when one is missing instead of "undefined/api/..." requests later.
import { z } from "zod/mini"

const required = z.string().check(z.trim(), z.minLength(1))

const EnvSchema = z.object({
    VITE_FIREBASE_API_KEY: required,
    VITE_FIREBASE_AUTH_DOMAIN: required,
    VITE_FIREBASE_PROJECT_ID: required,
    VITE_FIREBASE_STORAGE_BUCKET: required,
    VITE_FIREBASE_MESSAGING_SENDER_ID: required,
    VITE_FIREBASE_APP_ID: required,
    VITE_FINANSSI_API_URL: z.url({ protocol: /^https?$/ }),
})

export type Env = {
    firebase: { apiKey: string; authDomain: string; projectId: string; storageBucket: string; messagingSenderId: string; appId: string }
    /** The API's base URL without a trailing slash */
    apiUrl: string
    /** The STOMP endpoint: http(s)://host becomes ws(s)://host/ws */
    stompUrl: string
}

export const parseEnv = (raw: Record<string, unknown>): Env => {
    const result = EnvSchema.safeParse(raw)
    if (!result.success) {
        const names = [...new Set(result.error.issues.map((issue) => String(issue.path[0])))]
        throw new Error(`Missing or invalid settings: ${names.join(", ")}`)
    }
    const values = result.data
    const apiUrl = values.VITE_FINANSSI_API_URL.replace(/\/+$/, "")
    return {
        firebase: {
            apiKey: values.VITE_FIREBASE_API_KEY,
            authDomain: values.VITE_FIREBASE_AUTH_DOMAIN,
            projectId: values.VITE_FIREBASE_PROJECT_ID,
            storageBucket: values.VITE_FIREBASE_STORAGE_BUCKET,
            messagingSenderId: values.VITE_FIREBASE_MESSAGING_SENDER_ID,
            appId: values.VITE_FIREBASE_APP_ID,
        },
        apiUrl,
        stompUrl: `${apiUrl.replace(/^http/, "ws")}/ws`,
    }
}

let parsed: Env | undefined

/** The settings of this build; throws a descriptive error if one is missing */
export const env = (): Env => parsed ??= parseEnv(import.meta.env)

/** The API base URL; empty outside a Vite build (unit tests), where no request is made */
export const apiUrl = () => import.meta.env ? env().apiUrl : ""
