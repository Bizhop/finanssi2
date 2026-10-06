import { type ReactNode, useState } from "react"
import { Box, ButtonBase, Dialog, DialogContent, DialogTitle, IconButton, Stack, Tooltip, Typography } from "@mui/material"
import ZoomIn from "@mui/icons-material/ZoomIn"
import Close from "@mui/icons-material/Close"
import { BondCard, CarCard, cardPeekSlotProps, LoanCard, ShareCard, TitleDeedCard } from "./cards.tsx"
import { decisionLabels } from "./gameDecisions.ts"
import type { Game, GameBoardData } from "./gameApi.ts"

/** Scaled versions of the existing paper cards, with hover/focus peeks and a tap-to-read control. */
export const DecisionAsset = ({ asset, game, board, label, onSelect, disabled, children, selected }: {
    asset: string
    game: Game
    board: GameBoardData | null
    label?: string
    onSelect?: () => void
    disabled?: boolean
    selected?: boolean
    children?: ReactNode
}) => {
    const [peek, setPeek] = useState(false)
    const labels = decisionLabels(game, board)
    const name = asset.startsWith("B:")
        ? asset === "B:?" ? "Random bond" : `Bond ${asset.slice(2)}`
        : asset === "CAR"
        ? "Car"
        : asset === "LOAN"
        ? "Bank loan"
        : labels.asset(asset)
    const face = () => {
        if (asset.startsWith("B:")) return <BondCard number={asset === "B:?" ? "?" : Number(asset.slice(2))} />
        if (asset === "CAR") return <CarCard />
        if (asset === "LOAN") return <LoanCard />
        if (asset.startsWith("S:")) {
            const share = board?.shares.find((share) => share.id === asset.slice(2))
            return share ? <ShareCard share={share} board={board} /> : <Typography>{name}</Typography>
        }
        const deed = board?.titleDeeds.find((deed) => deed.square === Number(asset.slice(2)))
        const owned = game.state.properties.find((property) => property.square === deed?.square)
        return deed
            ? (
                <TitleDeedCard
                    deed={deed}
                    built={owned?.built}
                    mortgaged={owned?.mortgaged}
                    groupName={board?.groups.find((group) => group.id === deed.group)?.name}
                />
            )
            : <Typography>{name}</Typography>
    }
    return (
        <Stack spacing={0.5} sx={{ alignItems: "center", width: 100 }} data-decision-asset={asset}>
            <Box sx={{ position: "relative", p: 0.5, borderRadius: 1, outline: selected ? "3px solid" : undefined, outlineColor: "primary.main" }}>
                <Tooltip title={<Box sx={{ fontSize: 13 }}>{face()}</Box>} slotProps={cardPeekSlotProps}>
                    <ButtonBase
                        aria-label={label ?? `View ${name}`}
                        disabled={disabled}
                        onClick={onSelect ?? (() => setPeek(true))}
                        sx={{
                            display: "block",
                            fontSize: 6,
                            borderRadius: 1,
                            outlineOffset: 3,
                            "&:focus-visible": { outline: "2px solid", outlineColor: "primary.main" },
                            "&:hover": { transform: "translateY(-2px)" },
                            "&:disabled": { opacity: 0.5 },
                        }}
                    >
                        {face()}
                    </ButtonBase>
                </Tooltip>
                <Tooltip title={`View ${name}`}>
                    <IconButton
                        size="small"
                        aria-label={`Read ${name} card`}
                        onClick={() => setPeek(true)}
                        sx={{ position: "absolute", right: -4, bottom: -4, bgcolor: "background.paper", boxShadow: 1, p: 0.25 }}
                    >
                        <ZoomIn sx={{ fontSize: 18 }} />
                    </IconButton>
                </Tooltip>
            </Box>
            {children}
            <Dialog open={peek} onClose={() => setPeek(false)} maxWidth="xs">
                <DialogTitle sx={{ fontSize: 15, pr: 6 }}>
                    {name}
                    <IconButton aria-label="Close card" onClick={() => setPeek(false)} sx={{ position: "absolute", right: 8, top: 8 }}>
                        <Close />
                    </IconButton>
                </DialogTitle>
                <DialogContent sx={{ display: "flex", justifyContent: "center", fontSize: 16, py: 2 }}>{face()}</DialogContent>
            </Dialog>
        </Stack>
    )
}
