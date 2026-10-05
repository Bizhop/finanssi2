import { Route, Routes, useLocation } from "react-router"
import { Box, Container, Divider, Paper, Stack } from "@mui/material"

import FrontPage from "./FrontPage.tsx"
import Header from "./Header.tsx"
import Games from "./Games.tsx"
import GameRoom from "./GameRoom.tsx"
import { GAME_BOARD_MAX_WIDTH, GAME_ROOM_MAX_WIDTH } from "./boardLayout.ts"

const NotFound = () => (
    <Box sx={{ flexGrow: 1 }}>
        <h1>Page not found!</h1>
    </Box>
)

const MyRoutes = () => (
    <Routes>
        <Route path="/" element={<FrontPage />} />
        <Route path="/games" element={<Games />} />
        <Route path="/games/:id" element={<GameRoom />} />
        <Route path="*" element={<NotFound />} />
    </Routes>
)

const App = () => {
    const isGameRoom = useLocation().pathname.startsWith("/games/")
    return (
        <Container
            maxWidth={false}
            component={Paper}
            sx={{
                height: "100%",
                display: "flex",
                flexDirection: "column",
                pb: 2,
                ...(isGameRoom && {
                    "@media (min-width: 2200px) and (min-height: 1100px)": { maxWidth: GAME_ROOM_MAX_WIDTH },
                    "@media (min-width: 1536px) and (max-width: 2199.95px)": { maxWidth: GAME_BOARD_MAX_WIDTH },
                    "@media (min-width: 2200px) and (max-height: 1099.95px)": { maxWidth: GAME_BOARD_MAX_WIDTH },
                }),
            }}
        >
            <Stack direction="column" sx={{ flex: 1, minHeight: 0 }}>
                <Header />
                <Divider />
                <MyRoutes />
            </Stack>
        </Container>
    )
}

export default App
