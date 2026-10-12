// Rule logic the client works out itself from the game state and board data; the backend validates every command.
import type { Game, GameBoardData } from "../../api/gameApi.ts"

const BRANCH_OFFICE_SQUARE = 11
const HEAD_OFFICE_FIRST_SQUARE = 35
const PURCHASE_CERTIFICATES = ["PV-02", "PV-08"]

/**
 * Whether the bank sells properties or shares to the player right now: on their own turn before rolling, on the branch office (11)
 * or in the head office (35–46), one purchase per turn, and not while Finance News stops the trade (FL-21 properties, FL-09 shares)
 * unless they hold a purchase certificate. Cash is deliberately not required, so the player sees everything for sale; the backend
 * checks it when they buy.
 */
export const bankSalesOpen = (game: Game, playerId: string | null, kind: "property" | "share") => {
    const { state } = game
    const player = state.players.find((item) => item.playerId === playerId)
    if (!player || game.status !== "RUNNING" || state.currentPlayer !== playerId || state.phase !== "BEFORE_ROLL") return false
    if (state.pendingDecisions.length > 0 || state.boughtThisTurn) return false
    if (player.position !== BRANCH_OFFICE_SQUARE && player.position < HEAD_OFFICE_FIRST_SQUARE) return false
    const stopped = state.activeFinanceNews === (kind === "property" ? "FL-21" : "FL-09")
    return !stopped || player.heldStockTips.some((id) => PURCHASE_CERTIFICATES.includes(id))
}

/** What a takeover costs: the purchase prices of the other players' properties (with buildings) and shares in the group */
export const takeoverSum = (game: Game, board: GameBoardData | null, group: string, caller: string) => {
    const groupData = board?.groups.find((item) => item.id === group)
    const properties = (groupData?.properties ?? []).reduce((sum, square) => {
        const property = game.state.properties.find((item) => item.square === square)
        const deed = board?.titleDeeds.find((item) => item.square === square)
        if (!property?.owner || property.owner === caller || !deed) return sum
        return sum + deed.price + (property.built ? deed.building?.price ?? 0 : 0)
    }, 0)
    const shares = (board?.shares ?? []).filter((share) => share.group === group).reduce((sum, share) => {
        const owner = game.state.shares.find((item) => item.id === share.id)?.owner
        return owner && owner !== caller ? sum + share.value : sum
    }, 0)
    return properties + shares
}

/**
 * Groups where the player may call a shareholders' meeting: on their own turn before rolling, in the head office (35–46), unless
 * FL-15 stops meetings, when the player owns some of the group's properties and shares and other players own some. With the house
 * rule (the game's ALL_ASSETS_BOUGHT setting) every property and share of the group must also be bought from the bank. Cash isn't required here; the meeting dialog shows what the takeover costs.
 */
export const meetingGroups = (game: Game, board: GameBoardData | null, playerId: string | null) => {
    const { state } = game
    const player = state.players.find((item) => item.playerId === playerId)
    if (!player || !board || game.status !== "RUNNING" || state.currentPlayer !== playerId || state.phase !== "BEFORE_ROLL") return []
    if (state.pendingDecisions.length > 0 || player.position < HEAD_OFFICE_FIRST_SQUARE || state.activeFinanceNews === "FL-15") return []
    return board.groups.filter((group) => {
        const owners = [
            ...group.properties.map((square) => state.properties.find((item) => item.square === square)?.owner ?? null),
            ...board.shares.filter((share) => share.group === group.id).map((share) => state.shares.find((item) => item.id === share.id)?.owner ?? null),
        ]
        const wholeGroupRequired = (state.settings.shareholdersMeeting ?? "ALL_ASSETS_BOUGHT") === "ALL_ASSETS_BOUGHT"
        return (!wholeGroupRequired || owners.every((owner) => owner != null)) && owners.includes(player.playerId) &&
            owners.some((owner) => owner != null && owner !== player.playerId)
    }).map((group) => group.id)
}

/** Chance that two dice total at most `limit` */
export const twoDiceAtMost = (limit: number) => {
    let hits = 0
    for (let a = 1; a <= 6; a++) for (let b = 1; b <= 6; b++) if (a + b <= limit) hits++
    return hits / 36
}

/** Whether the owner of a property also owns the rest of its group, which doubles its rent */
export const rentDoubled = (game: Game, board: GameBoardData | null, square: number) => {
    const owner = game.state.properties.find((item) => item.square === square)?.owner
    const group = board?.groups.find((item) => item.properties.includes(square))
    return owner != null && group != null && group.properties.every((number) => game.state.properties.find((item) => item.square === number)?.owner === owner)
}
