import { assertEquals } from "@std/assert"
import type { CommandOptions, GameBoardData } from "../../api/gameApi.ts"
import { buildAction, NO_OPTIONS, propertyActions, shareActions } from "./assetActions.ts"

type Deed = GameBoardData["titleDeeds"][number]
const deed: Deed = {
    id: "HT-03",
    square: 3,
    name: "Ompelimo",
    group: "KASITEOLLISUUS",
    price: 10_000,
    building: { label: "Rakennus", price: 10_000 },
    rent: null,
    parkingFee: null,
    mortgage: { unbuilt: 10_000, built: 20_000 },
    redemption: { unbuilt: 11_000, built: 22_000 },
    buyBack: null,
}
const property = { square: 3, owner: "p1", mortgaged: false, built: false }
const labels = (actions: { label: string }[]) => actions.map((action) => action.label)

Deno.test("an action exists only for choices the backend offers, with the backend's amounts", () => {
    const options: CommandOptions = {
        ...NO_OPTIONS,
        build: [{ square: 3, amount: 20_000 }],
        mortgage: [{ square: 3, amount: 12_500 }],
        sellBackProperty: [{ square: 3, amount: 7_500 }],
    }
    const actions = propertyActions(options, property, deed)
    assertEquals(labels(actions), ["Build", "Mortgage", "Sell"])
    assertEquals(actions[0].request.command, { type: "Build", squares: [3] })
    // Amounts include Finance News modifiers, so they are the offered ones, not the printed ones
    assertEquals(String(actions[1].request.body).includes("12,500"), true)
    assertEquals(String(actions[2].request.body).includes("7,500"), true)
})

Deno.test("with no options there is nothing to do", () => {
    assertEquals(propertyActions(NO_OPTIONS, property, deed), [])
    assertEquals(buildAction(NO_OPTIONS, deed), null)
    assertEquals(shareActions(NO_OPTIONS, { id: "OS-1", group: null, value: 1, dividendPercent: 1, dividend: 1, buyBack: 1 }, "Fund"), [])
})

Deno.test("a mortgaged property can be redeemed at the offered price", () => {
    const options: CommandOptions = { ...NO_OPTIONS, redeem: [{ square: 3, amount: 11_000 }] }
    const actions = propertyActions(options, { ...property, mortgaged: true }, deed)
    assertEquals(labels(actions), ["Redeem"])
    assertEquals(actions[0].request.command, { type: "Redeem", square: 3 })
})
