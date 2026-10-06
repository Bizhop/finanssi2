import type { Game } from "../src/components/gameApi.ts"

type Decision = Game["state"]["pendingDecisions"][number]
type Fixture = { pending: Decision; commands: string[]; cash?: number }
const player = "tester"
const stockTip = (card: string, options: string[]): Fixture => ({
    pending: { type: "StockTipChoice", player, card, options },
    commands: ["ChooseStockTipOption"],
})
const raiseFunds = { type: "RaiseFunds", player, creditor: "pekka", amount: 20000, charges: [{ amount: 20000, reason: "RENT" }] }

// Same serialized decisions as the backend. These exercise presentation and command submission, not rule transitions.
export const decisionFixtures: Record<string, Fixture> = {
    "raise-funds": { pending: raiseFunds, cash: 1000, commands: ["Mortgage", "SellBackProperty", "SellBackShare"] },
    "raise-funds-assets": {
        pending: { ...raiseFunds, amount: 120000, charges: [{ amount: 120000, reason: "RENT" }] },
        cash: 1000,
        commands: ["TakeLoan", "SellCar", "Mortgage", "SellBackProperty", "SellBackShare"],
    },
    "news-payment": {
        pending: { ...raiseFunds, creditor: null, amount: 20000, charges: [{ amount: 20000, reason: "FINANCE_NEWS" }] },
        cash: 1000,
        commands: ["TakeLoan", "Mortgage", "SellBackProperty", "SellBackShare"],
    },
    "payment-ready": { pending: raiseFunds, cash: 25000, commands: ["Mortgage", "SellBackProperty", "SellBackShare", "Pay"] },
    "bankruptcy": {
        pending: { ...raiseFunds, creditor: null, amount: 1000000, charges: [{ amount: 1000000, reason: "STOCK_TIP" }] },
        cash: 1000,
        commands: ["Mortgage", "SellBackProperty", "SellBackShare", "DeclareBankruptcy"],
    },
    "bond-offer": { pending: { type: "BondOffer", player, after: "NONE" }, commands: ["BuyBond", "Pass"] },
    "bond-auction": { pending: { type: "BondAuction", player, order: [player, "olli", "pekka", "liisa"], index: 0 }, commands: ["BidBond"] },
    "property-auction": {
        pending: { type: "AssetAuction", player, seller: "olli", asset: "P:26", minimumBid: 40000, order: [player, "pekka", "liisa"], index: 0 },
        commands: ["BidAsset"],
    },
    "share-auction": {
        pending: { type: "AssetAuction", player, seller: "olli", asset: "S:OS-KEMIA-1", minimumBid: 50000, order: [player, "pekka", "liisa"], index: 0 },
        commands: ["BidAsset"],
    },
    "news-direction": { pending: { type: "NewsDirection", player, card: "FL-04" }, commands: ["ChooseNewsDirection"] },
    "PV-10": stockTip("PV-10", ["9,10", "9,19", "10,19"]),
    "fire-forced": stockTip("PV-10", ["9,19"]),
    "PV-17": stockTip("PV-17", ["3", "5", "8", "9", "10", "12", "18", "19", "22", "26", "27", "30"]),
    "PV-24": stockTip("PV-24", ["B:3", "B:4", "B:6", "B:7", "B:8", "B:9", "B:10", "B:11", "B:12"]),
    "bond-transfer": stockTip("PV-24", ["P:olli:2"]),
    "PV-26": stockTip("PV-26", ["PAY", "PASS"]),
    "travel-no-cash": { ...stockTip("PV-26", ["PAY", "PASS"]), cash: 1000 },
    "PV-30": stockTip("PV-30", ["JAIL", "BAIL"]),
    "PV-31": stockTip("PV-31", ["19"]),
    "PV-35": stockTip("PV-35", ["P:3", "P:19", "S:OS-FINANSSIYHTYMA-2"]),
    "PV-36": stockTip("PV-36", ["P:3", "P:19", "S:OS-FINANSSIYHTYMA-2"]),
    "PV-38": stockTip("PV-38", ["OS-FINANSSIYHTYMA-2|pekka|OS-TEKNIIKKA-1", "PASS"]),
}
