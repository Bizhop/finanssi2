import React from "react"
import { Route, Routes } from "react-router-dom"
import { Box, Container, Divider, Paper, Stack } from "@mui/material"

import FrontPage from "./FrontPage"
import Header from "./Header"
import Games from "./Games"

const NotFound = () =>
    <Box sx={{ flexGrow: 1 }}>
      <h1>Page not found!</h1>
    </Box>

const MyRoutes = () =>
    <Routes>
        <Route path="/" element={<FrontPage />} />
        <Route path="/games" element={<Games />} />
        <Route path="*" element={<NotFound />}/>
    </Routes>

const App = () => <Container component={Paper}>
    <Stack direction="column">
        <Header />
        <Divider />
        <MyRoutes />
    </Stack>
</Container>

export default App
