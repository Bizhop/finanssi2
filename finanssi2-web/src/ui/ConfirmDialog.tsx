import type { ReactNode } from "react"
import { useTranslation } from "react-i18next"
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
    choices?: { label: string; command: Command }[]
}

export const ConfirmDialog = ({ request, busy, onClose, onConfirm }: {
    request: ConfirmRequest | null
    busy: boolean
    onClose: () => void
    onConfirm: (command: Command) => void
}) => {
    const { t } = useTranslation()
    return (
        <Dialog open={request != null} onClose={onClose}>
            <DialogTitle>{request?.title}</DialogTitle>
            {request?.body && (
                <DialogContent>
                    <DialogContentText component="div">{request.body}</DialogContentText>
                </DialogContent>
            )}
            <DialogActions>
                <Button onClick={onClose}>{t("common.cancel")}</Button>
                {request?.choices?.map((choice) => (
                    <Button key={choice.label} variant="contained" disabled={busy} onClick={() => { onClose(); onConfirm(choice.command) }}>
                        {choice.label}
                    </Button>
                ))}
                {!request?.choices && <Button
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
                </Button>}
            </DialogActions>
        </Dialog>
    )
}
