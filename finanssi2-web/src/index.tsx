import { Fragment, type ReactNode, StrictMode } from "react"
import { useTranslation } from "react-i18next"
import ReactDOM from "react-dom/client"
import { BrowserRouter } from "react-router"

import { App } from "./App.tsx"
import { CurrentUserProvider, useCurrentUser } from "./auth/CurrentUserContext.tsx"
import { ErrorBoundary } from "./ui/ErrorBoundary.tsx"
import { PublicProfilesProvider } from "./auth/PublicProfiles.tsx"
import { StompProvider } from "./realtime/StompContext.tsx"
import { ToastContainer } from "react-toastify"
import { env } from "./env.ts"
import "./i18n/init.ts"

// The websocket is open only while a user is logged in, authenticated with their Firebase ID token
const StompConnection = ({ children }: { children: ReactNode }) => {
    const { user, profile, status } = useCurrentUser()
    return (
        <StompProvider
            key={user ? `${user.uid}:${profile?.id ?? status}` : "signed-out"}
            url={env().stompUrl}
            enabled={user !== null && status === "ready"}
            getAccessToken={() => user ? user.getIdToken() : Promise.reject(new Error("Not signed in"))}
        >
            {children}
        </StompProvider>
    )
}

// Remounts the page on a language change. Text computed outside components (event descriptions, labels) and cached by the React Compiler
// would otherwise stay in the old language until its inputs change; rooms and lists reload their data on mount.
const LanguageRemount = ({ children }: { children: ReactNode }) => {
    const { i18n } = useTranslation()
    return <Fragment key={i18n.language}>{children}</Fragment>
}

const container = document.getElementById("app")
if (!container) throw new Error("The page has no #app element")
const root = ReactDOM.createRoot(container)
root.render(
    <StrictMode>
        <BrowserRouter>
            <CurrentUserProvider>
                <StompConnection>
                    <PublicProfilesProvider>
                        <LanguageRemount>
                            <ErrorBoundary>
                                <App />
                            </ErrorBoundary>
                        </LanguageRemount>
                    </PublicProfilesProvider>
                    <ToastContainer autoClose={1500} position="top-center" />
                </StompConnection>
            </CurrentUserProvider>
        </BrowserRouter>
    </StrictMode>,
)
