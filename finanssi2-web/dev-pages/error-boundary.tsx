// A component that throws while rendering, inside the app's error boundary: the page shows the fallback with "Try again" and "Back to the
// start" instead of going blank. "Try again" renders the component again, which works after ?fixed or after the toggle below.
import { StrictMode, useState } from "react"
import ReactDOM from "react-dom/client"
import { Button, Stack } from "@mui/material"

import { ErrorBoundary } from "../src/ui/ErrorBoundary.tsx"
import { appRoot } from "./root.ts"

let broken = true

const Broken = () => {
    if (broken) throw new Error("Preview render error")
    return <p>Rendered fine.</p>
}

const Page = () => {
    const [attempt, setAttempt] = useState(0)
    return (
        <Stack spacing={2} sx={{ p: 2 }}>
            <Button onClick={() => (broken = !broken, setAttempt(attempt + 1))}>{broken ? "Fix the component" : "Break the component"}</Button>
            <ErrorBoundary key={attempt}>
                <Broken />
            </ErrorBoundary>
        </Stack>
    )
}

ReactDOM.createRoot(appRoot()).render(
    <StrictMode>
        <Page />
    </StrictMode>,
)
