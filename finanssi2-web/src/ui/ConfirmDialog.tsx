import type { ReactNode } from "react"
import { Button, Dialog, DialogActions, DialogContent, DialogContentText, DialogTitle } from "@mui/material"

export type Command = Record<string, unknown>
export type ConfirmRequest = {
    title: string
    body?: ReactNode
    confirmLabel: string
    command: Command
    danger?: boolean
    /** Shows the dialog for information but can't be confirmed, e.g. a purchase the player can't afford yet */
    disabled?: boolean
}

export const ConfirmDialog = ({ request, busy, onClose, onConfirm }: {
    request: ConfirmRequest | null
    busy: boolean
    onClose: () => void
    onConfirm: (command: Command) => void
}) => (
    <Dialog open={request != null} onClose={onClose}>
        <DialogTitle>{request?.title}</DialogTitle>
        {request?.body && (
            <DialogContent>
                <DialogContentText component="div">{request.body}</DialogContentText>
            </DialogContent>
        )}
        <DialogActions>
            <Button onClick={onClose}>Cancel</Button>
            <Button
                variant="contained"
                color={request?.danger ? "error" : "primary"}
                disabled={busy || request?.disabled}
                onClick={() => {
                    if (!request) return
                    onClose()
                    onConfirm(request.command)
                }}
            >
                {request?.confirmLabel}
            </Button>
        </DialogActions>
    </Dialog>
)
