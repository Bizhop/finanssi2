import React from "react"
import ReactDOM from "react-dom/client"
import { BrowserRouter } from "react-router-dom"
import { StompSessionProvider } from "react-stomp-hooks"

import App from "./components/App"
import {CurrentUserProvider} from "./components/CurrentUserContext"
import { ToastContainer } from "react-toastify"

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
    </BrowserRouter>
)
