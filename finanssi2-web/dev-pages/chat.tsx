// Logged-in front page with a chat history, loaded page by page as you scroll up. A floating button simulates messages arriving over the websocket.
import ReactDOM from "react-dom/client"
import { MemoryRouter } from "react-router"
import { mock } from "react-stomp-hooks"
import { Button } from "@mui/material"

import App from "../src/components/App.tsx"
import { CurrentUserProvider } from "../src/components/CurrentUserContext.tsx"
import { fakeBackend, fakeChatMessages, fakeLogin, nextFakeId, receiveFakeChatMessage } from "./mocks.ts"

const count = Number(new URLSearchParams(location.search).get("count") ?? 100)
fakeLogin()
fakeBackend(fakeChatMessages(count))

let received = 0
const simulateIncoming = () =>
    receiveFakeChatMessage({
        id: nextFakeId(),
        username: "other@example.com",
        name: "Olli Other",
        message: `Incoming message ${++received}`,
        timestamp: Date.now(),
        photoUrl: "",
    })

ReactDOM.createRoot(document.getElementById("app")!).render(
    <MemoryRouter>
        <mock.StompSessionProviderMock>
            <CurrentUserProvider>
                <App />
                <Button
                    variant="contained"
                    size="small"
                    onClick={simulateIncoming}
                    sx={{ position: "fixed", top: 16, right: "50%", transform: "translateX(50%)" }}
                >
                    Simulate incoming message
                </Button>
            </CurrentUserProvider>
        </mock.StompSessionProviderMock>
    </MemoryRouter>,
)
