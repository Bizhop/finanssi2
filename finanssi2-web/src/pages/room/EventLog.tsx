import { Box, Stack, Typography } from "@mui/material"

import type { GameBoardData } from "../../api/gameApi.ts"
import { describeEvent, type GameLogEntry } from "../../game/labels/gameEvents.ts"

const CardChapters = ({ card, fallback }: { card: GameBoardData["financeNews"][number] | undefined; fallback: string }) => (
    <Stack lang="fi" spacing={0.25}>
        {card?.chapters.map((chapter, index) => (
            <Typography
                key={index}
                variant="body2"
                sx={{ fontWeight: chapter.type === "header" ? 700 : undefined, fontStyle: chapter["font-style"] === "italic" ? "italic" : undefined }}
            >
                {chapter.text}
            </Typography>
        )) ?? <Typography variant="body2">{fallback}</Typography>}
    </Stack>
)

/** A log line; card draws also show the card text, turn starts separate turns */
const EventLine = ({ entry, text, board }: { entry: GameLogEntry; text: string; board: GameBoardData | null }) => {
    const cards = entry.type === "FinanceNewsDrawn" ? board?.financeNews : entry.type === "StockTipDrawn" ? board?.stockTips : undefined
    const card = cards?.find((item) => item.id === entry.event.card)
    const turnStart = entry.type === "TurnStarted"
    return (
        <Box sx={turnStart ? { borderTop: 1, borderColor: "divider", pt: 0.5 } : undefined}>
            <Typography
                variant="body2"
                color={entry.type === "NotImplemented" && !["BRANCH_OFFICE", "CONSTRUCTION"].includes(String(entry.event.squareType))
                    ? "warning.main"
                    : turnStart
                    ? "text.primary"
                    : "text.secondary"}
                sx={{ fontWeight: turnStart ? 600 : undefined }}
            >
                <Box component="span" sx={{ color: "text.disabled", mr: 0.75 }}>{entry.seq}</Box>
                {text}
            </Typography>
            {card && (
                <Box sx={{ pl: 2, borderLeft: 2, borderColor: "divider", ml: 0.5, my: 0.25 }}>
                    <CardChapters card={card} fallback={card.id} />
                </Box>
            )}
        </Box>
    )
}

const MAX_LINES = 200

/** The latest events, newest first */
export const EventLog = ({ events, board, playerName }: { events: GameLogEntry[]; board: GameBoardData | null; playerName: (playerId: string) => string }) => (
    <Stack spacing={0.5} sx={{ height: "100%", minHeight: 0, overflowY: "auto" }}>
        {events.slice(-MAX_LINES).reverse().map((entry) => (
            <EventLine key={entry.seq} entry={entry} text={describeEvent(entry, { playerName, board })} board={board} />
        ))}
    </Stack>
)
