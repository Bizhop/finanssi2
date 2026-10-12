import { Button, Dialog, DialogActions, DialogContent, DialogTitle, Stack } from "@mui/material"

import type { Game, GameBoardData } from "../../api/gameApi.ts"
import { ShareCard } from "../../game/components/Cards.tsx"
import { formatEuro } from "../../ui/format.ts"

/** The shares the bank sells, to pick one to buy */
export const BuyShareDialog = ({ open, game, board, cash, busy, onClose, onBuy }: {
    open: boolean
    game: Game
    board: GameBoardData | null
    cash: number
    busy: boolean
    onClose: () => void
    onBuy: (shareId: string) => void
}) => (
    <Dialog open={open} onClose={onClose} maxWidth="lg">
        <DialogTitle>Buy a share from the bank</DialogTitle>
        <DialogContent sx={{ fontSize: 13 }}>
            <Stack direction="row" spacing={2} useFlexGap sx={{ flexWrap: "wrap", py: 1 }}>
                {game.state.shares.filter((share) => share.owner === null).map((owned) => {
                    const share = board?.shares.find((item) => item.id === owned.id)
                    return share && (
                        <Stack key={share.id} spacing={0.75}>
                            <ShareCard share={share} board={board} />
                            <Button
                                variant="contained"
                                size="small"
                                // Shown for every share for sale; one the player can't afford yet can't be bought
                                disabled={busy || share.value > cash}
                                onClick={() => {
                                    onClose()
                                    onBuy(share.id)
                                }}
                            >
                                Buy {formatEuro(share.value)}
                            </Button>
                        </Stack>
                    )
                })}
            </Stack>
        </DialogContent>
        <DialogActions>
            <Button onClick={onClose}>Cancel</Button>
        </DialogActions>
    </Dialog>
)
