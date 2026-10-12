import { assertEquals, assertThrows } from "@std/assert"
import { GameViewSchema, PendingDecisionSchema } from "./schemas.ts"

const player = { playerId: "p1", piece: 0, cash: 75_000, position: 1, car: false, loans: 0, out: false, heldStockTips: [] }
const options = {
    buyProperty: [{ square: 3, amount: 15_000 }],
    buyShare: [],
    mortgage: [],
    redeem: [],
    sellBackProperty: [],
    sellBackShare: [],
    build: [],
    shareholdersMeeting: [{ group: "KEMIA", brokerageFees: [20_000, 30_000] }],
    buyBond: [1, 2],
    bondPrice: 500,
}
const view = () => ({
    game: {
        id: "g1",
        mode: "NORMAL",
        status: "RUNNING",
        creator: "p1",
        createdAt: 1,
        version: 3,
        // As the backend sends it: fields not listed in the schema are simply extra
        state: {
            players: [{ ...player, jailExemption: false }],
            turnOrder: ["p1"],
            currentPlayer: "p1",
            settings: { loanLimit: "UNLIMITED", compulsorySaleMinimumBid: "NONE" },
            activeFinanceNews: null,
            finished: false,
            winner: null,
            phase: "BEFORE_ROLL",
            finalStandings: [],
            properties: [{ square: 3, owner: null, mortgaged: false, built: false }],
            shares: [],
            bonds: [],
            pendingDecisions: [],
        },
    },
    allowedCommands: ["Roll"],
    actingPlayer: "p1",
    options,
})

Deno.test("a game view as the backend sends it parses, with defaults for what it leaves out", () => {
    const parsed = GameViewSchema.parse(view())
    assertEquals(parsed.game.state.settings.shareholdersMeeting, "ALL_ASSETS_BOUGHT")
    assertEquals(parsed.game.state.boughtThisTurn, false)
    assertEquals(parsed.game.state.players[0].name, "")
    assertEquals(parsed.game.state.players[0].photoUrl, null)
    assertEquals(parsed.options.shareholdersMeeting[0].brokerageFees, [20_000, 30_000])
})

Deno.test("a view without the options, or with a changed type, is rejected", () => {
    const withoutOptions: Record<string, unknown> = view()
    delete withoutOptions.options
    assertThrows(() => GameViewSchema.parse(withoutOptions))
    const wrongStatus = view()
    wrongStatus.game.status = "PAUSED"
    assertThrows(() => GameViewSchema.parse(wrongStatus))
})

Deno.test("pending decisions are told apart by type and carry their own fields", () => {
    const raise = PendingDecisionSchema.parse({ type: "RaiseFunds", player: "p1", creditor: null, amount: 5_000, charges: [{ amount: 5_000, reason: "RENT" }] })
    assertEquals(raise.type === "RaiseFunds" && raise.amount, 5_000)
    const choice = PendingDecisionSchema.parse({ type: "StockTipChoice", player: "p1", card: "PV-26", options: ["PAY", "PASS"] })
    assertEquals(choice.type === "StockTipChoice" && choice.options, ["PAY", "PASS"])
    assertThrows(() => PendingDecisionSchema.parse({ type: "Unknown", player: "p1" }))
    assertThrows(() => PendingDecisionSchema.parse({ type: "AssetAuction", player: "p1" }))
})
