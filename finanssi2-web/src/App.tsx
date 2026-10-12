import { lazy, Suspense } from "react"
import { Navigate, Route, Routes, useLocation } from "react-router"
import { Alert, Box, Container, Divider, Paper, Stack, Typography } from "@mui/material"
import { useTranslation } from "react-i18next"

import { FrontPage } from "./pages/FrontPage.tsx"
import { Header } from "./ui/Header.tsx"
import { Games } from "./pages/Games.tsx"
import { gameRoomWidthStyles } from "./game/rules/boardLayout.ts"
import { useCurrentUser } from "./auth/CurrentUserContext.tsx"
import { useStompProblem } from "./realtime/StompContext.tsx"
import { ErrorBoundary } from "./ui/ErrorBoundary.tsx"

// The room and the account page are the heaviest routes and are not needed on the front page
const GameRoom = lazy(() => import("./pages/GameRoom.tsx").then((module) => ({ default: module.GameRoom })))
const AccountProfile = lazy(() => import("./pages/AccountProfile.tsx").then((module) => ({ default: module.AccountProfile })))

const AuthenticatedRoute = ({ children }: { children: React.ReactNode }) => {
    const { t } = useTranslation()
    const { status } = useCurrentUser()
    if (status === "initializing" || status === "profileLoading") return <Typography sx={{ p: 2 }}>{t("common.loadingAccount")}</Typography>
    return status === "ready" ? children : <Navigate to="/" replace />
}

const NotFound = () => {
    const { t } = useTranslation()
    return (
        <Box sx={{ flexGrow: 1 }}>
            <Typography variant="h4" component="h1">{t("app.notFound")}</Typography>
        </Box>
    )
}

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
    const { t } = useTranslation()
    const connectionProblem = useStompProblem()
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
                ...(isGameRoom && gameRoomWidthStyles),
            }}
        >
            <Stack direction="column" sx={{ flex: 1, minHeight: 0 }}>
                <Header />
                <Divider />
                {connectionProblem && <Alert severity="warning" role="status">{t("app.connectionLost")}</Alert>}
                {/* Keyed by path, so leaving a broken page recovers */}
                <ErrorBoundary key={pathname}>
                    <Suspense fallback={<Typography sx={{ p: 2 }}>{t("common.loading")}</Typography>}>
                        <MyRoutes />
                    </Suspense>
                </ErrorBoundary>
            </Stack>
        </Container>
    )
}
