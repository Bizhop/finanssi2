import { type ReactNode, StrictMode } from "react"
import ReactDOM from "react-dom/client"
import { BrowserRouter } from "react-router"

import { App } from "./App.tsx"
import { CurrentUserProvider, useCurrentUser } from "./auth/CurrentUserContext.tsx"
import { ErrorBoundary } from "./ui/ErrorBoundary.tsx"
import { PublicProfilesProvider } from "./auth/PublicProfiles.tsx"
import { StompProvider } from "./realtime/StompContext.tsx"
import { ToastContainer } from "react-toastify"
import { z } from "zod/mini"
import { en } from "zod/locales"

// zod/mini ships without error messages; load the English locale once for the whole app
z.config(en())

// http(s)://host → ws(s)://host/ws
const stompUrl = `${import.meta.env.VITE_FINANSSI_API_URL.replace(/^http/, "ws")}/ws`

// The websocket is open only while a user is logged in, authenticated with their Firebase ID token
const StompConnection = ({ children }: { children: ReactNode }) => {
    const { user, profile, status } = useCurrentUser()
    return (
        <StompProvider
            key={user ? `${user.uid}:${profile?.id ?? status}` : "signed-out"}
            url={stompUrl}
            enabled={user !== null && status === "ready"}
            getAccessToken={() => user ? user.getIdToken() : Promise.reject(new Error("Not signed in"))}
        >
            {children}
        </StompProvider>
    )
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
                        <ErrorBoundary>
                            <App />
                        </ErrorBoundary>
                    </PublicProfilesProvider>
                    <ToastContainer autoClose={1500} position="top-center" />
                </StompConnection>
            </CurrentUserProvider>
        </BrowserRouter>
    </StrictMode>,
)
