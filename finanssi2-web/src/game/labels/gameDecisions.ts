import type { Game, GameBoardData } from "../../api/gameApi.ts"
import { formatEuro } from "../../ui/format.ts"
import { STOCK_TIP } from "../rules/stockTipCards.ts"

export const validAuctionBid = (input: string, cash: number, minimum: number) => {
    const amount = Number(input)
    return input.trim() !== "" && Number.isSafeInteger(amount) && amount >= 0 && amount % 500 === 0 && amount <= cash &&
        (amount === 0 || amount >= minimum)
}

export const decisionLabels = (game: Game, board: GameBoardData | null) => {
    const player = (playerId: string) => game.state.players.find((item) => item.playerId === playerId)?.name ?? playerId
    const property = (square: string) => board?.titleDeeds.find((item) => item.square === Number(square))?.name ?? `Property ${square}`
    const share = (id: string) => {
        const data = board?.shares.find((item) => item.id === id)
        const group = board?.groups.find((item) => item.id === data?.group)?.name ?? "Fund"
        return data ? `${group} share (${formatEuro(data.value)}, ${data.dividendPercent}% dividend, ${id.split("-").at(-1)})` : id
    }
    const asset = (id: string) => id.startsWith("P:") ? property(id.slice(2)) : id.startsWith("S:") ? share(id.slice(2)) : id
    const option = (card: string, value: string) => {
        const parts = value.split(":")
        switch (card) {
            case STOCK_TIP.FIRE:
                return `Burn buildings: ${value.split(",").map(property).join(" and ")}`
            case STOCK_TIP.MOVE_TO_PROPERTY:
                return `Move to ${property(value)}`
            case STOCK_TIP.FREE_BOND:
                return parts[0] === "B" ? `Take bond ${parts[1]} from the bank — free` : `Take bond ${parts[2]} from ${player(parts[1])} — free`
            case STOCK_TIP.TRAVEL:
                return value === "PAY" ? "Pay €25,000 to move to the bank entrance" : "Stay here"
            case STOCK_TIP.JAIL_OR_BAIL:
                return value === "JAIL" ? "Go to jail" : "Pay €30,000 bail"
            case STOCK_TIP.SELL_AT_NOMINAL: {
                const deed = board?.titleDeeds.find((item) => item.square === Number(value))
                const owned = game.state.properties.find((item) => item.square === Number(value))
                const price = deed ? deed.price + (owned?.built ? deed.building?.price ?? 0 : 0) : null
                return `Sell ${property(value)}${price === null ? "" : ` for ${formatEuro(price)}`}`
            }
            case STOCK_TIP.TRANSFER_ASSET:
                return `Transfer ${asset(value)} to your left neighbour for €10,000`
            case STOCK_TIP.COMPULSORY_SALE:
                return `Auction ${asset(value)}`
            case STOCK_TIP.SHARE_SWAP: {
                if (value === "PASS") return "Keep your shares"
                const [mine, other, theirs] = value.split("|")
                return `Swap your ${share(mine)} for ${player(other)}'s ${share(theirs)}`
            }
            default:
                return value
        }
    }
    return { player, property, share, asset, option }
}

/** Finance News movement stops on the bank's mandatory-stop squares, in either direction. */
export const newsDestination = (from: number, forward: boolean, board: GameBoardData | null) => {
    let to = from
    for (let step = 0; step < 3; step++) {
        to = (to - 1 + (forward ? 1 : -1) + 46) % 46 + 1
        if (board?.squares.find((square) => square.square === to)?.mandatoryStop ?? [1, 34].includes(to)) break
    }
    return to
}

export const nextAuctionBid = (input: string, direction: 1 | -1, minimum: number) => {
    const amount = Number(input) || 0
    const first = Math.max(500, Math.ceil(minimum / 500) * 500)
    if (direction === 1) return amount < first ? first : Math.floor(amount / 500) * 500 + 500
    return amount <= first ? 0 : Math.max(first, Math.ceil(amount / 500) * 500 - 500)
}
