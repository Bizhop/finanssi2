// Player actions tied to assets and their confirmations. The backend lists allowed command types only; which asset a command applies to is
// worked out here from the title deeds and shares, and the backend validates the exact command when it is sent.
import type { ReactNode } from "react"

import type { CommandOptions, Game, GameBoardData } from "../../api/gameApi.ts"
import type { Command, ConfirmRequest } from "../../ui/ConfirmDialog.tsx"
import { formatEuro } from "../../ui/format.ts"

export type AssetAction = { label: string; request: ConfirmRequest }

/** What the box of the player you control can do; absent for other players */
export type PlayerControls = {
    allowed: string[]
    /** The concrete choices behind the allowed commands, with current prices */
    options: CommandOptions
    busy: boolean
    send: (command: Command) => void
    confirm: (request: ConfirmRequest) => void
    /** Context-sensitive actions shown on their own row */
    contextActions?: ReactNode
    /** Given while the bank sells shares to the player: the shares icon glows and opens the purchase */
    onBuyShare?: () => void
}

type Property = Game["state"]["properties"][number]
type TitleDeed = GameBoardData["titleDeeds"][number]
type Share = GameBoardData["shares"][number]

// Fixed amounts that are not choices: the backend charges and pays exactly these
export const CAR_PRICE = 50_000
export const CAR_SELL_BACK_PRICE = 25_000
export const LOAN_AMOUNT = 50_000

/** No choices at all, e.g. for a player who is not the one to act */
export const NO_OPTIONS: CommandOptions = {
    buyProperty: [],
    buyShare: [],
    mortgage: [],
    redeem: [],
    sellBackProperty: [],
    sellBackShare: [],
    build: [],
    shareholdersMeeting: [],
    buyBond: [],
    bondPrice: 0,
}

const byState = (values: { unbuilt: number | null; built: number | null } | null, property: Property) => values?.[property.built ? "built" : "unbuilt"] ?? null

// What each action costs or pays comes from the backend's options (Finance News included); only the card text is local

export const buildAction = (options: CommandOptions, deed: TitleDeed): AssetAction | null => {
    const offer = options.build.find((item) => item.square === deed.square)
    return offer && deed.building
        ? {
            label: "Build",
            request: {
                title: `Build on ${deed.name}?`,
                body: `${deed.building.label === "Teollisuus" ? "An industrial plant" : "A building"} costs ${formatEuro(offer.amount)}.`,
                confirmLabel: "Build",
                command: { type: "Build", squares: [deed.square] },
            },
        }
        : null
}

export const propertyActions = (options: CommandOptions, property: Property, deed: TitleDeed): AssetAction[] => {
    const mortgage = options.mortgage.find((item) => item.square === deed.square)
    const redeem = options.redeem.find((item) => item.square === deed.square)
    const sell = options.sellBackProperty.find((item) => item.square === deed.square)
    const redemption = byState(deed.redemption, property)
    const actions: (AssetAction | undefined | null)[] = [
        buildAction(options, deed),
        mortgage && {
            label: "Mortgage",
            request: {
                title: `Mortgage ${deed.name}?`,
                body: `The bank lends you ${formatEuro(mortgage.amount)}. Redeeming it later costs ${
                    redemption != null ? formatEuro(redemption) : "the redemption value"
                }.`,
                confirmLabel: "Mortgage",
                command: { type: "Mortgage", square: deed.square },
            },
        },
        redeem && {
            label: "Redeem",
            request: {
                title: `Redeem ${deed.name}?`,
                body: `Redeeming costs ${formatEuro(redeem.amount)}.`,
                confirmLabel: "Redeem",
                command: { type: "Redeem", square: deed.square },
            },
        },
        sell && {
            label: "Sell",
            request: {
                title: `Sell ${deed.name} back to the bank?`,
                body: `The bank pays ${formatEuro(sell.amount)}${property.built ? ", including the building" : ""}.`,
                confirmLabel: "Sell",
                command: { type: "SellBackProperty", square: deed.square },
                danger: true,
            },
        },
    ]
    return actions.filter((action): action is AssetAction => Boolean(action))
}

export const shareActions = (options: CommandOptions, share: Share, name: string): AssetAction[] => {
    const offer = options.sellBackShare.find((item) => item.share === share.id)
    return offer
        ? [{
            label: "Sell",
            request: {
                title: `Sell the ${name} share back to the bank?`,
                body: `The bank pays ${formatEuro(offer.amount)}.`,
                confirmLabel: "Sell",
                command: { type: "SellBackShare", share: share.id },
                danger: true,
            },
        }]
        : []
}

/** Held Stock Tips that can be played on their own; the others are used automatically by the rules */
export const PLAYABLE_HELD_STOCK_TIPS = ["PV-25"]

export const stockTipAction = (allowed: string[], id: string, title: string): AssetAction | null =>
    allowed.includes("UseHeldStockTip") && PLAYABLE_HELD_STOCK_TIPS.includes(id)
        ? { label: "Use", request: { title: `Use “${title}”?`, confirmLabel: "Use", command: { type: "UseHeldStockTip", card: id } } }
        : null

export const carAction = (allowed: string[], hasCar: boolean): AssetAction | null =>
    !hasCar && allowed.includes("BuyCar")
        ? {
            label: "Buy car",
            request: {
                title: "Buy a car?",
                body: `A car costs ${formatEuro(CAR_PRICE)}. With a car you roll two dice on your turn (one inside the bank).`,
                confirmLabel: "Buy car",
                command: { type: "BuyCar" },
            },
        }
        : hasCar && allowed.includes("SellCar")
        ? {
            label: "Sell car",
            request: {
                title: "Sell your car back to the bank?",
                body: `The bank pays ${formatEuro(CAR_SELL_BACK_PRICE)}.`,
                confirmLabel: "Sell car",
                command: { type: "SellCar" },
                danger: true,
            },
        }
        : null

export const loanActions = (allowed: string[]): AssetAction[] => {
    const actions: (AssetAction | false)[] = [
        allowed.includes("TakeLoan") && {
            label: `Take a loan (+${formatEuro(LOAN_AMOUNT)})`,
            request: {
                title: "Take a bank loan?",
                body: `The bank lends you ${formatEuro(LOAN_AMOUNT)}. Interest is charged on square 1 and when you repay a loan on square 43.`,
                confirmLabel: "Take loan",
                command: { type: "TakeLoan" },
            },
        },
        allowed.includes("RepayLoan") && {
            label: `Repay a loan (−${formatEuro(LOAN_AMOUNT)})`,
            request: {
                title: "Repay a bank loan?",
                body: `You pay the bank ${formatEuro(LOAN_AMOUNT)}.`,
                confirmLabel: "Repay loan",
                command: { type: "RepayLoan" },
            },
        },
    ]
    return actions.filter((action): action is AssetAction => Boolean(action))
}
