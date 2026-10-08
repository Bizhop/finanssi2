// Logged-in front page with a chat history, loaded page by page as you scroll up. Floating buttons simulate messages arriving over the
// websocket, and the websocket disconnecting (messages sent meanwhile are fetched after reconnecting).
import { useMemo, useState } from "react"
import ReactDOM from "react-dom/client"
import { MemoryRouter } from "react-router"
import { Button, Stack } from "@mui/material"

import App from "../src/components/App.tsx"
import { CurrentUserProvider } from "../src/components/CurrentUserContext.tsx"
import { StompContext } from "../src/components/StompContext.tsx"
import { fakeBackend, fakeChatMessages, fakeLogin, fakeStompConnection, nextFakeId, receiveFakeChatMessage } from "./mocks.ts"

const count = Number(new URLSearchParams(location.search).get("count") ?? 100)
fakeLogin()
fakeBackend(fakeChatMessages(count))

let received = 0
const simulateIncoming = (messageCount: number) => {
    for (let i = 0; i < messageCount; i++) {
        receiveFakeChatMessage({
            id: nextFakeId(),
            userId: "other",
            name: "Olli Other",
            message: `Incoming message ${++received}`,
            timestamp: Date.now(),
            photoUrl: "",
        })
    }
}

const TestPage = () => {
    const [connected, setConnected] = useState(true)
    const connection = useMemo(() => fakeStompConnection(connected), [connected])

    return (
        <StompContext.Provider value={connection}>
            <CurrentUserProvider>
                <App />
                <Stack direction="row" spacing={1} sx={{ position: "fixed", top: 16, left: "50%", transform: "translateX(-50%)" }}>
                    <Button variant="contained" size="small" onClick={() => simulateIncoming(1)}>Incoming message</Button>
                    <Button variant="contained" size="small" onClick={() => simulateIncoming(25)}>25 incoming</Button>
                    <Button variant="contained" size="small" color={connected ? "warning" : "success"} onClick={() => setConnected(!connected)}>
                        {connected ? "Disconnect" : "Reconnect"}
                    </Button>
                </Stack>
            </CurrentUserProvider>
        </StompContext.Provider>
    )
}

ReactDOM.createRoot(document.getElementById("app")!).render(
    <MemoryRouter>
        <TestPage />
    </MemoryRouter>,
)
