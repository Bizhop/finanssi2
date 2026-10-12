import { assertAlmostEquals, assertEquals } from "@std/assert"
import type { Game, GameBoardData } from "../../api/gameApi.ts"
import { rentDoubled, takeoverSum, twoDiceAtMost } from "./gameRules.ts"

const board = {
    groups: [{ id: "G", name: "Group", color: "red", properties: [1, 2] }],
    titleDeeds: [
        { square: 1, price: 10_000, building: { label: "Rakennus", price: 5_000 } },
        { square: 2, price: 20_000, building: { label: "Rakennus", price: 5_000 } },
    ],
    shares: [{ id: "S1", group: "G", value: 7_500 }, { id: "S2", group: "G", value: 7_500 }],
} as unknown as GameBoardData
const game = (owners: [string | null, string | null], built = false, shares: [string | null, string | null] = [null, null]) =>
    ({
        state: {
            properties: [{ square: 1, owner: owners[0], built }, { square: 2, owner: owners[1], built: false }],
            shares: [{ id: "S1", owner: shares[0] }, { id: "S2", owner: shares[1] }],
        },
    }) as unknown as Game

Deno.test("the takeover sum counts what the other players own, buildings included", () => {
    assertEquals(takeoverSum(game(["a", "b"]), board, "G", "a"), 20_000)
    assertEquals(takeoverSum(game(["b", "b"], true, ["b", null]), board, "G", "a"), 10_000 + 5_000 + 20_000 + 7_500)
    assertEquals(takeoverSum(game(["a", "a"], false, ["a", "a"]), board, "G", "a"), 0)
    assertEquals(takeoverSum(game(["b", null]), null, "G", "a"), 0)
})

Deno.test("two dice total at most a limit with the usual odds", () => {
    assertEquals(twoDiceAtMost(1), 0)
    assertAlmostEquals(twoDiceAtMost(2), 1 / 36)
    assertAlmostEquals(twoDiceAtMost(7), 21 / 36)
    assertEquals(twoDiceAtMost(12), 1)
})

Deno.test("rent doubles only when the owner holds the whole group", () => {
    assertEquals(rentDoubled(game(["a", "a"]), board, 1), true)
    assertEquals(rentDoubled(game(["a", "b"]), board, 1), false)
    assertEquals(rentDoubled(game([null, null]), board, 1), false)
    assertEquals(rentDoubled(game(["a", "a"]), null, 1), false)
})
