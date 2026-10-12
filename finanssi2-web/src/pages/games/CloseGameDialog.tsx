import { Button, Dialog, DialogActions, DialogContent, DialogTitle } from "@mui/material"

/** Confirms ending a running game or deleting a debug game; `action` is null while closed */
export const CloseGameDialog = ({ action, busy, onCancel, onConfirm }: {
    action: "end" | "delete" | null
    busy: boolean
    onCancel: () => void
    onConfirm: () => void
}) => (
    <Dialog open={action !== null} onClose={onCancel}>
        <DialogTitle>{action === "delete" ? "Delete this debug game?" : "End this game?"}</DialogTitle>
        <DialogContent>
            {action === "delete" ? "This removes the game and all its history." : "The game will be closed without a winner."}
        </DialogContent>
        <DialogActions>
            <Button onClick={onCancel}>Cancel</Button>
            <Button variant="contained" color="error" disabled={busy} onClick={onConfirm}>
                {action === "delete" ? "Delete" : "End game"}
            </Button>
        </DialogActions>
    </Dialog>
)
