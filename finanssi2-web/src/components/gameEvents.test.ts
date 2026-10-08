import type { GameBoardData } from "./gameApi.ts"
import { describeEvent, GameLogEntry } from "./gameEvents.ts"

const board = {
    squares: [{ square: 5, name: "Maalaamo", type: "PROPERTY", group: "KASITEOLLISUUS", price: 20000, text: null }],
    groups: [{ id: "KASITEOLLISUUS", name: "Käsiteollisuus Oy", color: "", properties: [5] }],
    financeNews: [],
    stockTips: [{ id: "PV-03", type: "PORSSIVIHJE", chapters: [{ type: "header", text: "Rahasto-osakeanti", "font-style": null }] }],
} as unknown as GameBoardData
const context = { playerName: (playerId: string) => ({ a: "Alice", b: "Bob" }[playerId] ?? playerId), board }
const entry = (type: string, event: Record<string, unknown>): GameLogEntry => ({ id: "g:1", seq: 1, time: 0, type, event: { type, ...event } })
const expect = (actual: string, expected: string) => {
    if (actual !== expected) throw new Error(`Expected "${expected}", got "${actual}"`)
}

Deno.test("event descriptions name players, squares, dice, cards and money", () => {
    expect(describeEvent(entry("DiceRolled", { player: "a", dice: [3, 4] }), context), "Alice rolled 3 + 4 = 7")
    expect(describeEvent(entry("LandedOn", { player: "a", square: 5 }), context), "Alice landed on Maalaamo (5)")
    expect(describeEvent(entry("StockTipDrawn", { player: "b", card: "PV-03", held: false }), context), "Bob drew Stock Tip “Rahasto-osakeanti”")
    expect(
        describeEvent(entry("MoneyTransferred", { from: "a", to: null, amount: 12500, reason: "RENT" }), context),
        `Alice paid €${(12500).toLocaleString()} to the bank (rent)`,
    )
    expect(describeEvent(entry("ShareBought", { player: "a", share: "OS-KASITEOLLISUUS-2" }), context), "Alice bought Käsiteollisuus Oy share 2")
    expect(
        describeEvent(entry("NotImplemented", { player: "a", square: 5, squareType: "BRANCH_OFFICE" }), context),
        "Maalaamo (5): no landing effect; buying is allowed here before the next roll",
    )
    expect(
        describeEvent(entry("NotImplemented", { player: "a", square: 5, squareType: "MOVE_TO" }), context),
        "Not implemented: Maalaamo (5) (MOVE_TO) has no effect yet",
    )
    expect(describeEvent(entry("SomethingNew", {}), context), "SomethingNew")
})
