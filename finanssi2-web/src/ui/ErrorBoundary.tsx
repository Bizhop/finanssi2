import { Component, type ErrorInfo, type ReactNode } from "react"
import { Alert, Button, Stack } from "@mui/material"

type Props = { children: ReactNode; onReset?: () => void }
type State = { failed: boolean }

/** Catches render errors below it, so one broken component shows a message instead of blanking the whole app */
export class ErrorBoundary extends Component<Props, State> {
    override state: State = { failed: false }

    static getDerivedStateFromError(): State {
        return { failed: true }
    }

    override componentDidCatch(error: unknown, info: ErrorInfo) {
        console.error("Render error", error, info.componentStack)
    }

    private readonly reset = () => {
        this.setState({ failed: false })
        this.props.onReset?.()
    }

    override render() {
        if (!this.state.failed) return this.props.children
        return (
            <Stack spacing={2} sx={{ p: 2, maxWidth: 520 }} role="alert">
                <Alert severity="error">Something went wrong while showing this page.</Alert>
                <Stack direction="row" spacing={1}>
                    <Button variant="contained" onClick={this.reset}>Try again</Button>
                    <Button onClick={() => location.assign("/")}>Back to the start</Button>
                </Stack>
            </Stack>
        )
    }
}
