import { useState } from "react"
import { Button, Dialog, DialogActions, DialogContent, DialogTitle, Stack, TextField, Typography } from "@mui/material"

import { twoDiceAtMost } from "../../game/rules/gameRules.ts"
import { formatEuro } from "../../ui/format.ts"

const BROKERAGE_FEES = [20000, 30000, 40000, 50000, 60000, 70000, 80000, 90000, 100000, 110000, 120000]

/** Calling a shareholders' meeting for a group: the takeover cost and the brokerage fee, with the chance of success of each fee */
export const MeetingDialog = ({ groupName, takeover, cash, busy, onClose, onCall }: {
    /** Null while closed */
    groupName: string | null
    takeover: number
    cash: number
    busy: boolean
    onClose: () => void
    onCall: (brokerageFee: number) => void
}) => {
    const [fee, setFee] = useState(BROKERAGE_FEES[0])
    const open = groupName !== null
    const short = takeover + fee > cash
    return (
        <Dialog open={open} onClose={onClose} maxWidth="xs" fullWidth>
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
                        {BROKERAGE_FEES.map((option) => (
                            <option key={option} value={option} disabled={takeover + option > cash}>
                                {formatEuro(option)}:{" "}
                                {Math.round(twoDiceAtMost(option / 10000) * 100)}% chance{takeover + option > cash ? " (not enough cash)" : ""}
                            </option>
                        ))}
                    </TextField>
                    <Typography variant="body2" color={short ? "error" : "text.secondary"}>
                        {short
                            ? `You need ${formatEuro(takeover + fee)}: raise ${formatEuro(takeover + fee - cash)} more first, e.g. by mortgaging or selling.`
                            : `The takeover succeeds on a dice total of ${fee / 10000} or less.`}
                    </Typography>
                </Stack>
            </DialogContent>
            <DialogActions>
                <Button onClick={onClose}>Cancel</Button>
                <Button
                    variant="contained"
                    disabled={short || busy}
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
