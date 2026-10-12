import { useTranslation } from "react-i18next"
import { Button, Dialog, DialogActions, DialogContent, DialogTitle, Stack } from "@mui/material"

import type { CommandOptions, GameBoardData } from "../../api/gameApi.ts"
import { ShareCard } from "../../game/components/Cards.tsx"
import { formatEuro } from "../../ui/format.ts"

/** The shares the player can buy from the bank right now, each at its current price */
export const BuyShareDialog = ({ open, offers, board, busy, onClose, onBuy }: {
    open: boolean
    offers: CommandOptions["buyShare"]
    board: GameBoardData | null
    busy: boolean
    onClose: () => void
    onBuy: (shareId: string) => void
}) => {
    const { t } = useTranslation()
    return (
        <Dialog open={open} onClose={onClose} maxWidth="lg">
            <DialogTitle>{t("room.buyShare.title")}</DialogTitle>
            <DialogContent sx={{ fontSize: 13 }}>
                <Stack direction="row" spacing={2} useFlexGap sx={{ flexWrap: "wrap", py: 1 }}>
                    {offers.map((offer) => {
                        const share = board?.shares.find((item) => item.id === offer.share)
                        return share && (
                            <Stack key={share.id} spacing={0.75}>
                                <ShareCard share={share} board={board} />
                                <Button
                                    variant="contained"
                                    size="small"
                                    disabled={busy}
                                    onClick={() => {
                                        onClose()
                                        onBuy(share.id)
                                    }}
                                >
                                    {t("room.buyShare.buy", { price: formatEuro(offer.amount) })}
                                </Button>
                            </Stack>
                        )
                    })}
                </Stack>
            </DialogContent>
            <DialogActions>
                <Button onClick={onClose}>{t("common.cancel")}</Button>
            </DialogActions>
        </Dialog>
    )
}
