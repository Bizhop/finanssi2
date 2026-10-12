import type { Game, GameBoardData } from "../../api/gameApi.ts"
import { t } from "../../i18n/index.ts"
import { formatEuro, formatList } from "../../ui/format.ts"
import { STOCK_TIP, TRAVEL_FEE } from "../rules/stockTipCards.ts"

/** Fixed amounts of Stock Tip choices: the bail of PV-30 and what the left neighbour pays for an asset with PV-35 */
export const BAIL_AMOUNT = 30_000
const TRANSFER_PRICE = 10_000

export const validAuctionBid = (input: string, cash: number, minimum: number) => {
    const amount = Number(input)
    return input.trim() !== "" && Number.isSafeInteger(amount) && amount >= 0 && amount % 500 === 0 && amount <= cash &&
        (amount === 0 || amount >= minimum)
}

export const decisionLabels = (game: Game, board: GameBoardData | null) => {
    const player = (playerId: string) => game.state.players.find((item) => item.playerId === playerId)?.name ?? playerId
    const property = (square: string) => board?.titleDeeds.find((item) => item.square === Number(square))?.name ?? t("decisionLabel.property", { square })
    const share = (id: string) => {
        const data = board?.shares.find((item) => item.id === id)
        const group = board?.groups.find((item) => item.id === data?.group)?.name ?? t("decisionLabel.fund")
        return data
            ? t("decisionLabel.share", {
                group,
                value: formatEuro(data.value),
                percent: String(data.dividendPercent),
                number: id.split("-").at(-1) ?? id,
            })
            : id
    }
    const asset = (id: string) => id.startsWith("P:") ? property(id.slice(2)) : id.startsWith("S:") ? share(id.slice(2)) : id
    /** What the bank pays for a property bought back at its nominal price, with the building if it has one */
    const nominalSalePrice = (square: string) => {
        const deed = board?.titleDeeds.find((item) => item.square === Number(square))
        const owned = game.state.properties.find((item) => item.square === Number(square))
        return deed ? deed.price + (owned?.built ? deed.building?.price ?? 0 : 0) : null
    }
    const option = (card: string, value: string) => {
        const parts = value.split(":")
        switch (card) {
            case STOCK_TIP.FIRE:
                return t("decisionLabel.burn", { properties: formatList(value.split(",").map(property)) })
            case STOCK_TIP.MOVE_TO_PROPERTY:
                return t("decisionLabel.moveTo", { property: property(value) })
            case STOCK_TIP.FREE_BOND:
                return parts[0] === "B"
                    ? t("decisionLabel.freeBondBank", { number: parts[1] })
                    : t("decisionLabel.freeBondPlayer", { number: parts[2], player: player(parts[1]) })
            case STOCK_TIP.TRAVEL:
                return value === "PAY" ? t("decisionLabel.travelPay", { fee: formatEuro(TRAVEL_FEE) }) : t("decisionLabel.travelStay")
            case STOCK_TIP.JAIL_OR_BAIL:
                return value === "JAIL" ? t("decisionLabel.jail") : t("decisionLabel.bail", { amount: formatEuro(BAIL_AMOUNT) })
            case STOCK_TIP.SELL_AT_NOMINAL: {
                const price = nominalSalePrice(value)
                return price === null
                    ? t("decisionLabel.sell", { property: property(value) })
                    : t("decisionLabel.sellFor", { property: property(value), price: formatEuro(price) })
            }
            case STOCK_TIP.TRANSFER_ASSET:
                return t("decisionLabel.transfer", { asset: asset(value), price: formatEuro(TRANSFER_PRICE) })
            case STOCK_TIP.COMPULSORY_SALE:
                return t("decisionLabel.auction", { asset: asset(value) })
            case STOCK_TIP.SHARE_SWAP: {
                if (value === "PASS") return t("decisionLabel.keepShares")
                const [mine, other, theirs] = value.split("|")
                return t("decisionLabel.swap", { mine: share(mine), player: player(other), theirs: share(theirs) })
            }
            default:
                return value
        }
    }
    return { player, property, share, asset, option, nominalSalePrice }
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
