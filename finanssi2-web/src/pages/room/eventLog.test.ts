import { assertEquals } from "@std/assert"
import type { GameLogEntry } from "../../game/labels/gameEvents.ts"
import { hasEventGap, mergeEvents, turnStockTip } from "./eventLog.ts"

const entry = (seq: number, type = "DiceRolled", event: Record<string, unknown> = {}): GameLogEntry => ({ id: `g:${seq}`, seq, time: 0, type, event })
const seqs = (entries: GameLogEntry[]) => entries.map((item) => item.seq)

Deno.test("merging events keeps each sequence number once, oldest first", () => {
    assertEquals(seqs(mergeEvents([entry(3), entry(1)], [entry(2), entry(3)])), [1, 2, 3])
    assertEquals(seqs(mergeEvents([], [])), [])
})

Deno.test("a broadcast that continues the log has no gap", () => {
    assertEquals(hasEventGap(4, [entry(5), entry(6)]), false)
    assertEquals(hasEventGap(0, [entry(1)]), false)
    // Already known events are no gap either
    assertEquals(hasEventGap(6, [entry(5), entry(6)]), false)
})

Deno.test("skipped events are a gap, whether at the start or in the middle", () => {
    assertEquals(hasEventGap(4, [entry(6)]), true)
    assertEquals(hasEventGap(4, [entry(5), entry(7)]), true)
})

Deno.test("the face-up Stock Tip is the one drawn since the turn started", () => {
    const tip = entry(4, "StockTipDrawn", { card: "PV-03" })
    assertEquals(turnStockTip([entry(1, "TurnStarted"), tip, entry(5)]), tip)
    assertEquals(turnStockTip([entry(1, "StockTipDrawn", { card: "PV-02" }), entry(2, "TurnStarted"), entry(3)]), undefined)
    assertEquals(turnStockTip([]), undefined)
})
