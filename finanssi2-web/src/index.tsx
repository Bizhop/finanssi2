import { type ReactNode, StrictMode } from "react"
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
