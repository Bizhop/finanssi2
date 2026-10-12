// Player actions tied to assets and their confirmations. The backend lists allowed command types only; which asset a command applies to is
// worked out here from the title deeds and shares, and the backend validates the exact command when it is sent.
import type { ReactNode } from "react"

import type { CommandOptions, Game, GameBoardData } from "../../api/gameApi.ts"
import { t } from "../../i18n/index.ts"
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
            label: t("asset.build"),
            request: {
                title: t("asset.build.title", { name: deed.name }),
                body: t(deed.building.label === "Teollisuus" ? "asset.build.industrial" : "asset.build.building", { amount: formatEuro(offer.amount) }),
                confirmLabel: t("asset.build"),
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
            label: t("asset.mortgage"),
            request: {
                title: t("asset.mortgage.title", { name: deed.name }),
                body: t("asset.mortgage.body", {
                    amount: formatEuro(mortgage.amount),
                    redemption: redemption != null ? formatEuro(redemption) : t("asset.mortgage.redemptionValue"),
                }),
                confirmLabel: t("asset.mortgage"),
                command: { type: "Mortgage", square: deed.square },
            },
        },
        redeem && {
            label: t("asset.redeem"),
            request: {
                title: t("asset.redeem.title", { name: deed.name }),
                body: t("asset.redeem.body", { amount: formatEuro(redeem.amount) }),
                confirmLabel: t("asset.redeem"),
                command: { type: "Redeem", square: deed.square },
            },
        },
        sell && {
            label: t("asset.sell"),
            request: {
                title: t("asset.sellProperty.title", { name: deed.name }),
                body: t(property.built ? "asset.sellProperty.bodyBuilt" : "asset.sellProperty.body", { amount: formatEuro(sell.amount) }),
                confirmLabel: t("asset.sell"),
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
            label: t("asset.sell"),
            request: {
                title: t("asset.sellShare.title", { name }),
                body: t("asset.sellShare.body", { amount: formatEuro(offer.amount) }),
                confirmLabel: t("asset.sell"),
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
        ? {
            label: t("asset.use"),
            request: { title: t("asset.use.title", { title }), confirmLabel: t("asset.use"), command: { type: "UseHeldStockTip", card: id } },
        }
        : null

export const carAction = (allowed: string[], hasCar: boolean): AssetAction | null =>
    !hasCar && allowed.includes("BuyCar")
        ? {
            label: t("asset.buyCar"),
            request: {
                title: t("asset.buyCar.title"),
                body: t("asset.buyCar.body", { price: formatEuro(CAR_PRICE) }),
                confirmLabel: t("asset.buyCar"),
                command: { type: "BuyCar" },
            },
        }
        : hasCar && allowed.includes("SellCar")
        ? {
            label: t("asset.sellCar"),
            request: {
                title: t("asset.sellCar.title"),
                body: t("asset.sellCar.body", { price: formatEuro(CAR_SELL_BACK_PRICE) }),
                confirmLabel: t("asset.sellCar"),
                command: { type: "SellCar" },
                danger: true,
            },
        }
        : null

export const loanActions = (allowed: string[]): AssetAction[] => {
    const amount = formatEuro(LOAN_AMOUNT)
    const actions: (AssetAction | false)[] = [
        allowed.includes("TakeLoan") && {
            label: t("asset.takeLoan", { amount }),
            request: {
                title: t("asset.takeLoan.title"),
                body: t("asset.takeLoan.body", { amount }),
                confirmLabel: t("asset.takeLoan.confirm"),
                command: { type: "TakeLoan" },
            },
        },
        allowed.includes("RepayLoan") && {
            label: t("asset.repayLoan", { amount }),
            request: {
                title: t("asset.repayLoan.title"),
                body: t("asset.repayLoan.body", { amount }),
                confirmLabel: t("asset.repayLoan.confirm"),
                command: { type: "RepayLoan" },
            },
        },
    ]
    return actions.filter((action): action is AssetAction => Boolean(action))
}
