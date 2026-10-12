import { useState } from "react"
import { useTranslation } from "react-i18next"
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
    const { t } = useTranslation()
    const [nextNews, setNextNews] = useState("")
    const [nextTip, setNextTip] = useState("")
    return (
        <Stack spacing={1} sx={{ height: "100%", minHeight: 0, overflowY: "auto" }}>
            <Alert severity="info">{t("room.debug.controlling", { name: controlling ?? "—" })}</Alert>
            <Stack direction={{ xs: "column", sm: "row" }} spacing={1} useFlexGap sx={{ alignItems: "flex-start", flexWrap: "wrap" }}>
                <TextField
                    size="small"
                    label={t("room.debug.dice")}
                    placeholder={t("room.debug.dicePlaceholder")}
                    value={diceInput}
                    onChange={(event) => onDiceChange(event.target.value)}
                    helperText={t("room.debug.diceHelp")}
                />
                {running && !hasPending && (
                    <>
                        <TextField
                            select
                            size="small"
                            label={t("room.debug.nextNews")}
                            value={nextNews}
                            onChange={(event) => setNextNews(event.target.value)}
                            slotProps={{ select: { native: true }, inputLabel: { shrink: true } }}
                        >
                            <option value="">{t("room.debug.choose")}</option>
                            {board?.financeNews.map((card) => <option key={card.id} value={card.id}>{card.id} · {card.chapters[0]?.text}</option>)}
                        </TextField>
                        <Button disabled={!nextNews || busy} onClick={() => onSelectCard("FINANCE_NEWS", nextNews)}>{t("room.debug.setNews")}</Button>
                        <TextField
                            select
                            size="small"
                            label={t("room.debug.nextTip")}
                            value={nextTip}
                            onChange={(event) => setNextTip(event.target.value)}
                            slotProps={{ select: { native: true }, inputLabel: { shrink: true } }}
                        >
                            <option value="">{t("room.debug.choose")}</option>
                            {board?.stockTips.filter((card) => !game.state.players.some((player) => player.heldStockTips.includes(card.id))).map((card) => (
                                <option key={card.id} value={card.id}>{card.id} · {card.chapters[0]?.text}</option>
                            ))}
                        </TextField>
                        <Button disabled={!nextTip || busy} onClick={() => onSelectCard("STOCK_TIP", nextTip)}>{t("room.debug.setTip")}</Button>
                    </>
                )}
            </Stack>
        </Stack>
    )
}
