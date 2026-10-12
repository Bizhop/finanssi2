import { useState } from "react"
import { Alert, Button, Stack, TextField } from "@mui/material"

import type { Game, GameBoardData } from "../../api/gameApi.ts"

/** Debug games: whose seat is controlled, dice for the next command, and the next Finance News and Stock Tip card */
export const DebugPanel = ({ game, board, controlling, running, hasPending, busy, diceInput, onDiceChange, onSelectCard }: {
    game: Game
    board: GameBoardData | null
    controlling: string | undefined
    running: boolean
    hasPending: boolean
    busy: boolean
    diceInput: string
    onDiceChange: (value: string) => void
    onSelectCard: (deck: string, card: string) => void
}) => {
    const [nextNews, setNextNews] = useState("")
    const [nextTip, setNextTip] = useState("")
    return (
        <Stack spacing={1} sx={{ height: "100%", minHeight: 0, overflowY: "auto" }}>
            <Alert severity="info">Controlling: {controlling ?? "—"}</Alert>
            <Stack direction={{ xs: "column", sm: "row" }} spacing={1} useFlexGap sx={{ alignItems: "flex-start", flexWrap: "wrap" }}>
                <TextField
                    size="small"
                    label="Dice for next submitted command"
                    placeholder="e.g. 3, 6"
                    value={diceInput}
                    onChange={(event) => onDiceChange(event.target.value)}
                    helperText="Optional; unused values are discarded."
                />
                {running && !hasPending && (
                    <>
                        <TextField
                            select
                            size="small"
                            label="Next Finance News"
                            value={nextNews}
                            onChange={(event) => setNextNews(event.target.value)}
                            slotProps={{ select: { native: true }, inputLabel: { shrink: true } }}
                        >
                            <option value="">Choose…</option>
                            {board?.financeNews.map((card) => <option key={card.id} value={card.id}>{card.id} · {card.chapters[0]?.text}</option>)}
                        </TextField>
                        <Button disabled={!nextNews || busy} onClick={() => onSelectCard("FINANCE_NEWS", nextNews)}>Set next news</Button>
                        <TextField
                            select
                            size="small"
                            label="Next Stock Tip"
                            value={nextTip}
                            onChange={(event) => setNextTip(event.target.value)}
                            slotProps={{ select: { native: true }, inputLabel: { shrink: true } }}
                        >
                            <option value="">Choose…</option>
                            {board?.stockTips.filter((card) => !game.state.players.some((player) => player.heldStockTips.includes(card.id))).map((card) => (
                                <option key={card.id} value={card.id}>{card.id} · {card.chapters[0]?.text}</option>
                            ))}
                        </TextField>
                        <Button disabled={!nextTip || busy} onClick={() => onSelectCard("STOCK_TIP", nextTip)}>Set next tip</Button>
                    </>
                )}
            </Stack>
        </Stack>
    )
}
