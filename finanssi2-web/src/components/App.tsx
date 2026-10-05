import { Route, Routes } from "react-router"
import { Box, Container, Divider, Paper, Stack } from "@mui/material"

import FrontPage from "./FrontPage.tsx"
import Header from "./Header.tsx"
import Games from "./Games.tsx"
import GameRoom from "./GameRoom.tsx"

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

const App = () => (
    <Container maxWidth={false} component={Paper} sx={{ height: "100%", display: "flex", flexDirection: "column", pb: 2 }}>
        <Stack direction="column" sx={{ flex: 1, minHeight: 0 }}>
            <Header />
            <Divider />
            <MyRoutes />
        </Stack>
    </Container>
)

export default App
