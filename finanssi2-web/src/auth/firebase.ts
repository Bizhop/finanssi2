import { initializeApp } from "firebase/app"
import { browserLocalPersistence, browserPopupRedirectResolver, initializeAuth } from "firebase/auth"

import { env } from "../env.ts"

const firebaseApp = initializeApp(env().firebase)
export const auth = initializeAuth(firebaseApp, {
    persistence: browserLocalPersistence,
    popupRedirectResolver: browserPopupRedirectResolver,
})
