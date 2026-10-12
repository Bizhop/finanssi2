// Rule logic the client works out itself from the game state and board data; the backend validates every command.
import type { Game, GameBoardData } from "../../api/gameApi.ts"

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
