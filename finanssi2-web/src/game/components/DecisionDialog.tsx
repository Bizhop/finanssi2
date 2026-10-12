import { type RefObject, useLayoutEffect, useState } from "react"
import { Box, Chip, Dialog, DialogContent, DialogTitle, Stack, Tooltip, Typography } from "@mui/material"
import PaymentsOutlined from "@mui/icons-material/PaymentsOutlined"
import GavelOutlined from "@mui/icons-material/GavelOutlined"
import type { CommandOptions, Game, GameBoardData } from "../../api/gameApi.ts"
import { decisionLabels } from "../labels/gameDecisions.ts"
import { STOCK_TIP } from "../rules/stockTipCards.ts"
import { formatEuro } from "../../ui/format.ts"
import { AuctionBody } from "./decision/AuctionBody.tsx"
import { BondOfferBody } from "./decision/BondOfferBody.tsx"
import { NewsDirectionBody } from "./decision/NewsDirectionBody.tsx"
import { RaiseFundsBody } from "./decision/RaiseFundsBody.tsx"
import type { DecisionEnv, Pending } from "./decision/shared.tsx"
import { StockTipChoiceBody } from "./decision/StockTipChoiceBody.tsx"

/** A modal anchored to the board's open area. Its portal and backdrop leave the game layout unchanged. */
export const DecisionDialog = ({ pending, allowed, options, send, game, board, busy, anchor, sourceStockTip }: {
    pending: Pending
    allowed: string[]
    options: CommandOptions
    send: (command: Record<string, unknown>) => Promise<void>
    game: Game
    board: GameBoardData | null
    busy: boolean
    anchor: RefObject<HTMLDivElement | null>
    sourceStockTip?: string | null
}) => {
    const [position, setPosition] = useState({ top: 80, left: globalThis.innerWidth / 2 })
    // The Stock Tip or Finance News card the decision comes from, when it has one
    const pickCard = "card" in pending ? pending.card : undefined
    useLayoutEffect(() => {
        const update = () => {
            const rect = anchor.current?.getBoundingClientRect()
            if (!rect) return
            const width = Math.min(pickCard === STOCK_TIP.MOVE_TO_PROPERTY ? 580 : 500, innerWidth - 24)
            const top = Math.max(12, Math.min(rect.top + rect.height * 0.22, innerHeight * 0.28))
            setPosition({ top, left: Math.max(width / 2 + 12, Math.min(rect.left + rect.width / 2, innerWidth - width / 2 - 12)) })
        }
        update()
        const observer = new ResizeObserver(update)
        if (anchor.current) observer.observe(anchor.current)
        globalThis.addEventListener("resize", update)
        globalThis.addEventListener("scroll", update, true)
        return () => {
            observer.disconnect()
            globalThis.removeEventListener("resize", update)
            globalThis.removeEventListener("scroll", update, true)
        }
    }, [anchor, pickCard])

    const labels = decisionLabels(game, board)
    const actor = game.state.players.find((player) => player.playerId === pending.player)
    const cash = actor?.cash ?? 0
    const charges = pending.type === "RaiseFunds" ? pending.charges : []
    const source = pickCard ??
        (pending.type === "AssetAuction"
            ? STOCK_TIP.COMPULSORY_SALE
            : pending.type === "RaiseFunds"
            ? charges.some((charge) => charge.reason === "FINANCE_NEWS") ? game.state.activeFinanceNews : sourceStockTip
            : null)
    const card = [...board?.stockTips ?? [], ...board?.financeNews ?? []].find((card) => card.id === source)
    const headings: Partial<Record<Pending["type"], string>> = {
        RaiseFunds: "Settle payment",
        BondOffer: "Choose a bond",
        BondAuction: "Bond auction",
        AssetAuction: "Asset auction",
    }
    const heading = card?.chapters.find((chapter) => chapter.type === "header")?.text ?? headings[pending.type] ?? "Your decision"
    const minimum = pending.type === "AssetAuction" ? pending.minimumBid : options.bondPrice
    const singleFire = pending.type === "StockTipChoice" && pending.card === STOCK_TIP.FIRE && pending.options.length === 1
    const env: DecisionEnv = { game, board, allowed, options, send, busy, labels, cash, position: actor?.position ?? 1 }
    const summary = pending.type === "RaiseFunds"
        ? `${formatEuro(pending.amount)} to ${pending.creditor ? labels.player(pending.creditor) : "the bank"}`
        : pending.type === "BondOffer"
        ? `${formatEuro(options.bondPrice)} per bond`
        : pending.type === "AssetAuction"
        ? `Minimum bid ${formatEuro(minimum)} · sealed bids`
        : pending.type === "BondAuction"
        ? "Sealed bids · the winner receives a random available bond"
        : pickCard === STOCK_TIP.FIRE
        ? singleFire ? "These buildings must be returned. Continue to roll for insurance." : "Choose two buildings to return."
        : pickCard === STOCK_TIP.MOVE_TO_PROPERTY
        ? "Select a highlighted property on the board."
        : null
    return (
        <Dialog
            open
            aria-labelledby="decision-title"
            aria-describedby="decision-description"
            maxWidth={false}
            slotProps={{
                backdrop: { sx: { bgcolor: "rgba(70, 76, 82, 0.32)" } },
                paper: {
                    sx: {
                        position: "fixed",
                        top: position.top,
                        left: position.left,
                        transform: "translateX(-50%)",
                        m: 0,
                        width: "calc(100vw - 24px)",
                        maxWidth: pickCard === STOCK_TIP.MOVE_TO_PROPERTY ? 580 : 500,
                        maxHeight: `calc(100dvh - ${position.top + 12}px)`,
                        borderRadius: 2,
                    },
                },
            }}
        >
            <DialogTitle id="decision-title" sx={{ px: 2, pt: 1.5, pb: 0.75 }}>
                <Typography variant="overline" color="text.secondary" sx={{ lineHeight: 1.4 }}>Your decision</Typography>
                <Typography component="span" sx={{ display: "block", fontSize: 18, fontWeight: 600, lineHeight: 1.3 }}>{heading}</Typography>
            </DialogTitle>
            <DialogContent sx={{ px: 2, pb: 2 }}>
                <Stack spacing={1.25}>
                    <Box id="decision-description">
                        {card?.chapters.filter((_, index) => index !== card.chapters.findIndex((chapter) => chapter.type === "header")).map((
                            chapter,
                            index,
                        ) => (
                            <Typography
                                key={index}
                                variant="body2"
                                sx={{ mb: 0.5, fontStyle: chapter["font-style"], fontWeight: chapter.type === "header" ? 600 : undefined }}
                            >
                                {chapter.text}
                            </Typography>
                        ))}
                        {summary && <Typography variant="body2" color="text.secondary">{summary}</Typography>}
                    </Box>
                    <Stack direction="row" spacing={1} useFlexGap sx={{ flexWrap: "wrap" }}>
                        <Tooltip title="Available cash">
                            <Chip size="small" icon={<PaymentsOutlined />} label={formatEuro(cash)} />
                        </Tooltip>
                        {pending.type === "RaiseFunds" && (
                            <Tooltip title="Still needed to settle this payment">
                                <Chip size="small" color="warning" label={`−${formatEuro(Math.max(0, pending.amount - cash))}`} />
                            </Tooltip>
                        )}
                        {(pending.type === "BondAuction" || pending.type === "AssetAuction") && (
                            <Tooltip title="Minimum nonzero bid">
                                <Chip size="small" icon={<GavelOutlined />} label={formatEuro(minimum)} />
                            </Tooltip>
                        )}
                    </Stack>
                    <Box component="fieldset" disabled={busy} sx={{ border: 0, p: 0, m: 0, minWidth: 0, "&:disabled": { opacity: 0.6 } }}>
                        <Stack spacing={1.25}>
                            {pending.type === "StockTipChoice" && <StockTipChoiceBody pending={pending} env={env} />}
                            {pending.type === "BondOffer" && <BondOfferBody env={env} />}
                            {pending.type === "NewsDirection" && <NewsDirectionBody env={env} />}
                            {(pending.type === "BondAuction" || pending.type === "AssetAuction") && (
                                <AuctionBody pending={pending} env={env} minimum={minimum} />
                            )}
                            {pending.type === "RaiseFunds" && <RaiseFundsBody env={env} />}
                        </Stack>
                    </Box>
                </Stack>
            </DialogContent>
        </Dialog>
    )
}
