import { Route, Routes } from "react-router"
import { Box, Container, Divider, Paper, Stack } from "@mui/material"

import FrontPage from "./FrontPage.tsx"
import Header from "./Header.tsx"
import Games from "./Games.tsx"

const NotFound = () => (
    <Box sx={{ flexGrow: 1 }}>
        <h1>Page not found!</h1>
    </Box>
)

const MyRoutes = () => (
    <Routes>
        <Route path="/" element={<FrontPage />} />
        <Route path="/games" element={<Games />} />
        <Route path="*" element={<NotFound />} />
    </Routes>
)

const App = () => (
    <Container component={Paper}>
        <Stack direction="column">
            <Header />
            <Divider />
            <MyRoutes />
        </Stack>
    </Container>
)

export default App
