import type { GameLogEntry } from "../../game/labels/gameEvents.ts"

/** Union by sequence number, oldest first */
export const mergeEvents = (current: GameLogEntry[], incoming: GameLogEntry[]) =>
    [...new Map([...current, ...incoming].map((entry) => [entry.seq, entry])).values()].sort((a, b) => a.seq - b.seq)

/** Whether a broadcast skips events after the last one known, so the missing ones must be fetched */
export const hasEventGap = (lastKnownSeq: number, incoming: GameLogEntry[]) => incoming.some((entry, index) => entry.seq > lastKnownSeq + index + 1)

/** Only a Stock Tip drawn during the current turn stays face up on the board */
export const turnStockTip = (events: GameLogEntry[]) =>
    events.slice(events.findLastIndex((entry) => entry.type === "TurnStarted") + 1).findLast((entry) => entry.type === "StockTipDrawn")

/** The latest Finance News drawn this turn stays visible in its board slot. */
export const turnFinanceNews = (events: GameLogEntry[]) =>
    events.slice(events.findLastIndex((entry) => entry.type === "TurnStarted") + 1).findLast((entry) => entry.type === "FinanceNewsDrawn")
