import { useState } from "react"
import { Button, Dialog, DialogActions, DialogContent, DialogTitle, Stack, TextField, Typography } from "@mui/material"

import { twoDiceAtMost } from "../../game/rules/gameRules.ts"
import { formatEuro } from "../../ui/format.ts"

/**
 * Calling a shareholders' meeting for a group: the takeover cost, and a brokerage fee out of those the backend accepts (it already
 * leaves out the fees the player cannot afford), with the chance of success of each.
 */
export const MeetingDialog = ({ groupName, takeover, fees, cash, busy, onClose, onCall }: {
    /** Null while closed */
    groupName: string | null
    takeover: number
    /** The brokerage fees the backend accepts for this meeting */
    fees: number[]
    cash: number
    busy: boolean
    onClose: () => void
    onCall: (brokerageFee: number) => void
}) => {
    const [fee, setFee] = useState(fees[0] ?? 0)
    return (
        <Dialog open={groupName !== null} onClose={onClose} maxWidth="xs" fullWidth>
            <DialogTitle>Shareholders' meeting: {groupName}</DialogTitle>
            <DialogContent>
                <Stack spacing={2} sx={{ pt: 1 }}>
                    <Typography variant="body2">
                        Taking over the other players' properties and shares costs {formatEuro(takeover)}, plus the brokerage fee. You have {formatEuro(cash)}.
                    </Typography>
                    <TextField
                        select
                        size="small"
                        label="Brokerage fee"
                        value={fee}
                        onChange={(event) => setFee(Number(event.target.value))}
                        slotProps={{ select: { native: true } }}
                    >
                        {fees.map((option) => (
                            <option key={option} value={option}>
                                {formatEuro(option)}: {Math.round(twoDiceAtMost(option / 10000) * 100)}% chance
                            </option>
                        ))}
                    </TextField>
                    <Typography variant="body2" color="text.secondary">
                        The takeover succeeds on a dice total of {fee / 10000} or less.
                    </Typography>
                </Stack>
            </DialogContent>
            <DialogActions>
                <Button onClick={onClose}>Cancel</Button>
                <Button
                    variant="contained"
                    disabled={busy || !fees.includes(fee)}
                    onClick={() => {
                        onClose()
                        onCall(fee)
                    }}
                >
                    Call meeting
                </Button>
            </DialogActions>
        </Dialog>
    )
}
