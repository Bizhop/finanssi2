import type { Game, GameBoardData } from "./gameApi.ts"
import { decisionLabels, newsDestination, nextAuctionBid, propertySaleProceeds, shareSaleProceeds, validAuctionBid } from "./gameDecisions.ts"

const game = {
    state: {
        players: [{ playerId: "olli", name: "Olli Other" }],
        properties: [{ square: 19, built: true, mortgaged: false }],
    },
} as unknown as Game
const board = {
    titleDeeds: [
        { square: 9, name: "Hotelli" },
        { square: 19, name: "Luottolaitos", price: 25000, building: { price: 35000 } },
    ],
    shares: [{ id: "OS-FINANSSIYHTYMA-2", group: "FINANSSIYHTYMA", value: 75000, dividendPercent: 30 }],
    groups: [{ id: "FINANSSIYHTYMA", name: "Finanssiyhtymä" }],
} as unknown as GameBoardData

const expect = (actual: unknown, expected: unknown) => {
    if (actual !== expected) throw new Error(`Expected ${JSON.stringify(expected)}, got ${JSON.stringify(actual)}`)
}

Deno.test("sealed bids allow a pass but reject blank, fractional, unaffordable and below-minimum bids", () => {
    for (const invalid of ["", " ", "-500", "1", "500.5", "1000", "10500", "NaN", "Infinity"]) {
        expect(validAuctionBid(invalid, 10000, 2000), false)
    }
    for (const valid of ["0", "2000", "2500", "10000"]) expect(validAuctionBid(valid, 10000, 2000), true)
    expect(validAuctionBid("500", 1000, 0), true)
    expect(validAuctionBid("500", 1000, 500), true)
})

Deno.test("Stock Tip labels decode bond recipients, building pairs, shares and nominal property sales", () => {
    const labels = decisionLabels(game, board)
    expect(labels.option("PV-24", "B:3"), "Take bond 3 from the bank — free")
    expect(labels.option("PV-24", "P:olli:2"), "Take bond 2 from Olli Other — free")
    expect(labels.option("PV-10", "9,19"), "Burn buildings: Hotelli and Luottolaitos")
    expect(labels.option("PV-17", "9"), "Move to Hotelli")
    expect(labels.option("PV-31", "19"), "Sell Luottolaitos for €60,000")
    expect(labels.option("PV-35", "P:19"), "Transfer Luottolaitos to your left neighbour for €10,000")
    expect(labels.option("PV-36", "S:OS-FINANSSIYHTYMA-2"), "Auction Finanssiyhtymä share (€75,000, 30% dividend, 2)")
    expect(labels.option("PV-26", "PAY"), "Pay €25,000 to move to the bank entrance")
    expect(labels.option("PV-26", "PASS"), "Stay here")
    expect(labels.option("PV-30", "BAIL"), "Pay €30,000 bail")
    expect(labels.option("PV-30", "JAIL"), "Go to jail")
    expect(labels.option("PV-38", "PASS"), "Keep your shares")
    expect(
        labels.option("PV-38", "OS-FINANSSIYHTYMA-2|olli|OS-FINANSSIYHTYMA-2"),
        "Swap your Finanssiyhtymä share (€75,000, 30% dividend, 2) for Olli Other's Finanssiyhtymä share (€75,000, 30% dividend, 2)",
    )
})

Deno.test("auction steps jump from passing to the minimum and back, in €500 increments", () => {
    expect(nextAuctionBid("0", 1, 40000), 40000)
    expect(nextAuctionBid("40000", 1, 40000), 40500)
    expect(nextAuctionBid("40000", -1, 40000), 0)
    expect(nextAuctionBid("40500", -1, 40000), 40000)
    expect(nextAuctionBid("0", 1, 0), 500)
    expect(nextAuctionBid("0", 1, 750), 1000)
})

Deno.test("Finance News landing previews follow mandatory stops in both directions", () => {
    expect(newsDestination(31, true, null), 34)
    expect(newsDestination(31, false, null), 28)
    expect(newsDestination(33, true, null), 34)
    expect(newsDestination(35, false, null), 34)
    expect(newsDestination(2, false, null), 1)
    expect(newsDestination(45, true, null), 1)
    expect(newsDestination(1, false, null), 44)
})

Deno.test("bank sale proceeds follow Finance News and round halved share proceeds up to €500", () => {
    expect(shareSaleProceeds(37500, "FL-06"), 19000)
    expect(shareSaleProceeds(37500, "FL-08"), 19000)
    expect(shareSaleProceeds(37500, "FL-09"), 19000)
    expect(shareSaleProceeds(37500, "FL-20"), 75000)
    expect(shareSaleProceeds(37500, null), 37500)
    expect(propertySaleProceeds(10000, false, "FL-11"), 15000)
    expect(propertySaleProceeds(10000, true, "FL-11"), 10000)
    expect(propertySaleProceeds(10000, false, "FL-14"), 10000)
})
