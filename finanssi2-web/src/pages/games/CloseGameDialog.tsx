import { useTranslation } from "react-i18next"
import { Button, Dialog, DialogActions, DialogContent, DialogTitle } from "@mui/material"

/** Confirms ending a running game or deleting a debug game; `action` is null while closed */
export const CloseGameDialog = ({ action, busy, onCancel, onConfirm }: {
    action: "end" | "delete" | null
    busy: boolean
    onCancel: () => void
    onConfirm: () => void
}) => {
    const { t } = useTranslation()
    return (
        <Dialog open={action !== null} onClose={onCancel}>
            <DialogTitle>{action === "delete" ? t("closeGame.deleteTitle") : t("closeGame.endTitle")}</DialogTitle>
            <DialogContent>
                {action === "delete" ? t("closeGame.deleteBody") : t("closeGame.endBody")}
            </DialogContent>
            <DialogActions>
                <Button onClick={onCancel}>{t("common.cancel")}</Button>
                <Button variant="contained" color="error" disabled={busy} onClick={onConfirm}>
                    {action === "delete" ? t("gameCard.delete") : t("gameCard.end")}
                </Button>
            </DialogActions>
        </Dialog>
    )
}
