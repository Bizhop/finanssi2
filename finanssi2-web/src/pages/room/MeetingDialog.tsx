import { useState } from "react"
import { useTranslation } from "react-i18next"
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
    const { t } = useTranslation()
    const [fee, setFee] = useState(fees[0] ?? 0)
    return (
        <Dialog open={groupName !== null} onClose={onClose} maxWidth="xs" fullWidth>
            <DialogTitle>{t("room.meeting.title", { group: groupName })}</DialogTitle>
            <DialogContent>
                <Stack spacing={2} sx={{ pt: 1 }}>
                    <Typography variant="body2">
                        {t("room.meeting.body", { takeover: formatEuro(takeover), cash: formatEuro(cash) })}
                    </Typography>
                    <TextField
                        select
                        size="small"
                        label={t("room.meeting.fee")}
                        value={fee}
                        onChange={(event) => setFee(Number(event.target.value))}
                        slotProps={{ select: { native: true } }}
                    >
                        {fees.map((option) => (
                            <option key={option} value={option}>
                                {t("room.meeting.feeOption", { fee: formatEuro(option), chance: Math.round(twoDiceAtMost(option / 10000) * 100) })}
                            </option>
                        ))}
                    </TextField>
                    <Typography variant="body2" color="text.secondary">
                        {t("room.meeting.limit", { limit: fee / 10000 })}
                    </Typography>
                </Stack>
            </DialogContent>
            <DialogActions>
                <Button onClick={onClose}>{t("common.cancel")}</Button>
                <Button
                    variant="contained"
                    disabled={busy || !fees.includes(fee)}
                    onClick={() => {
                        onClose()
                        onCall(fee)
                    }}
                >
                    {t("room.meeting.call")}
                </Button>
            </DialogActions>
        </Dialog>
    )
}
