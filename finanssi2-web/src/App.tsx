import { Navigate, Route, Routes, useLocation } from "react-router"
import { Box, Container, Divider, Paper, Stack, Typography } from "@mui/material"

import { FrontPage } from "./pages/FrontPage.tsx"
import { Header } from "./ui/Header.tsx"
import { Games } from "./pages/Games.tsx"
import { GameRoom } from "./pages/GameRoom.tsx"
import { AccountProfile } from "./pages/AccountProfile.tsx"
import { GAME_BOARD_MAX_WIDTH, GAME_ROOM_MAX_WIDTH } from "./game/rules/boardLayout.ts"
import { useCurrentUser } from "./auth/CurrentUserContext.tsx"
import { ErrorBoundary } from "./ui/ErrorBoundary.tsx"

const AuthenticatedRoute = ({ children }: { children: React.ReactNode }) => {
    const { status } = useCurrentUser()
    if (status === "initializing" || status === "profileLoading") return <Typography sx={{ p: 2 }}>Loading account…</Typography>
    return status === "ready" ? children : <Navigate to="/" replace />
}

const NotFound = () => (
    <Box sx={{ flexGrow: 1 }}>
        <h1>Page not found!</h1>
    </Box>
)

const MyRoutes = () => (
    <Routes>
        <Route path="/" element={<FrontPage />} />
        <Route
            path="/games"
            element={
                <AuthenticatedRoute>
                    <Games />
                </AuthenticatedRoute>
            }
        />
        <Route
            path="/games/:id"
            element={
                <AuthenticatedRoute>
                    <GameRoom />
                </AuthenticatedRoute>
            }
        />
        <Route
            path="/account"
            element={
                <AuthenticatedRoute>
                    <AccountProfile />
                </AuthenticatedRoute>
            }
        />
        <Route path="*" element={<NotFound />} />
    </Routes>
)

export const App = () => {
    const { pathname } = useLocation()
    const isGameRoom = pathname.startsWith("/games/")
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
                {/* Keyed by path, so leaving a broken page recovers */}
                <ErrorBoundary key={pathname}>
                    <MyRoutes />
                </ErrorBoundary>
            </Stack>
        </Container>
    )
}
