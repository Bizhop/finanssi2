import ReactDOM from "react-dom/client"
import { BrowserRouter } from "react-router"
import { StompSessionProvider } from "react-stomp-hooks"

import App from "./components/App.tsx"
import { CurrentUserProvider } from "./components/CurrentUserContext.tsx"
import { ToastContainer } from "react-toastify"
import { z } from "zod/mini"
import { en } from "zod/locales"

// zod/mini ships without error messages; load the English locale once for the whole app
z.config(en())

const container = document.getElementById("app")!
const root = ReactDOM.createRoot(container)
root.render(
    <BrowserRouter>
        <StompSessionProvider url={`${import.meta.env.VITE_FINANSSI_API_URL}/ws`}>
            <CurrentUserProvider>
                <App />
                <ToastContainer autoClose={1500} position="top-center" />
            </CurrentUserProvider>
        </StompSessionProvider>
    </BrowserRouter>,
)
