// Player actions tied to assets and their confirmations. The backend lists allowed command types only; which asset a command applies to is
// worked out here from the title deeds and shares, and the backend validates the exact command when it is sent.
import type { ReactNode } from "react"
import { Button, Dialog, DialogActions, DialogContent, DialogContentText, DialogTitle } from "@mui/material"

import type { Game, GameBoardData } from "./gameApi.ts"

export type Command = Record<string, unknown>
export type ConfirmRequest = {
    title: string
    body?: ReactNode
    confirmLabel: string
    command: Command
    danger?: boolean
    /** Shows the dialog for information but can't be confirmed, e.g. a purchase the player can't afford yet */
    disabled?: boolean
}
export type AssetAction = { label: string; request: ConfirmRequest }

/** What the box of the player you control can do; absent for other players */
export type PlayerControls = {
    allowed: string[]
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

// Printed values; Finance News can change some of them, and the backend charges the actual amount
export const CAR_PRICE = 50_000
export const CAR_SELL_BACK_PRICE = 25_000
export const LOAN_AMOUNT = 50_000

const euros = (value: number) => `€${value.toLocaleString()}`
const byState = (values: { unbuilt: number | null; built: number | null } | null, property: Property) => values?.[property.built ? "built" : "unbuilt"] ?? null

export const buildAction = (allowed: string[], property: Property, deed: TitleDeed): AssetAction | null =>
    allowed.includes("Build") && !property.built && !property.mortgaged && deed.building
        ? {
            label: "Build",
            request: {
                title: `Build on ${deed.name}?`,
                body: `${deed.building.label === "Teollisuus" ? "An industrial plant" : "A building"} costs ${euros(deed.building.price)}.`,
                confirmLabel: "Build",
                command: { type: "Build", squares: [deed.square] },
            },
        }
        : null

export const propertyActions = (allowed: string[], property: Property, deed: TitleDeed): AssetAction[] => {
    const mortgage = byState(deed.mortgage, property)
    const redemption = byState(deed.redemption, property)
    const buyBack = byState(deed.buyBack, property)
    const actions: (AssetAction | false | null)[] = [
        buildAction(allowed, property, deed),
        allowed.includes("Mortgage") && !property.mortgaged && mortgage != null && {
            label: "Mortgage",
            request: {
                title: `Mortgage ${deed.name}?`,
                body: `The bank lends you ${euros(mortgage)}. Redeeming it later costs ${redemption != null ? euros(redemption) : "the redemption value"}.`,
                confirmLabel: "Mortgage",
                command: { type: "Mortgage", square: deed.square },
            },
        },
        allowed.includes("Redeem") && property.mortgaged && {
            label: "Redeem",
            request: {
                title: `Redeem ${deed.name}?`,
                body: redemption != null ? `Redeeming costs ${euros(redemption)}.` : undefined,
                confirmLabel: "Redeem",
                command: { type: "Redeem", square: deed.square },
            },
        },
        allowed.includes("SellBackProperty") && !property.mortgaged && buyBack != null && {
            label: "Sell",
            request: {
                title: `Sell ${deed.name} back to the bank?`,
                body: `The bank pays ${euros(buyBack)}${property.built ? ", including the building" : ""}.`,
                confirmLabel: "Sell",
                command: { type: "SellBackProperty", square: deed.square },
                danger: true,
            },
        },
    ]
    return actions.filter((action): action is AssetAction => Boolean(action))
}

export const shareActions = (allowed: string[], share: Share, name: string): AssetAction[] =>
    allowed.includes("SellBackShare")
        ? [{
            label: "Sell",
            request: {
                title: `Sell the ${name} share back to the bank?`,
                body: `The bank pays ${euros(share.buyBack)}.`,
                confirmLabel: "Sell",
                command: { type: "SellBackShare", share: share.id },
                danger: true,
            },
        }]
        : []

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
                body: `A car costs ${euros(CAR_PRICE)}. With a car you roll two dice on your turn (one inside the bank).`,
                confirmLabel: "Buy car",
                command: { type: "BuyCar" },
            },
        }
        : hasCar && allowed.includes("SellCar")
        ? {
            label: "Sell car",
            request: {
                title: "Sell your car back to the bank?",
                body: `The bank pays ${euros(CAR_SELL_BACK_PRICE)}.`,
                confirmLabel: "Sell car",
                command: { type: "SellCar" },
                danger: true,
            },
        }
        : null

export const loanActions = (allowed: string[]): AssetAction[] => {
    const actions: (AssetAction | false)[] = [
        allowed.includes("TakeLoan") && {
            label: `Take a loan (+${euros(LOAN_AMOUNT)})`,
            request: {
                title: "Take a bank loan?",
                body: `The bank lends you ${euros(LOAN_AMOUNT)}. Interest is charged on square 1 and when you repay a loan on square 43.`,
                confirmLabel: "Take loan",
                command: { type: "TakeLoan" },
            },
        },
        allowed.includes("RepayLoan") && {
            label: `Repay a loan (−${euros(LOAN_AMOUNT)})`,
            request: {
                title: "Repay a bank loan?",
                body: `You pay the bank ${euros(LOAN_AMOUNT)}.`,
                confirmLabel: "Repay loan",
                command: { type: "RepayLoan" },
            },
        },
    ]
    return actions.filter((action): action is AssetAction => Boolean(action))
}

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

export const ConfirmDialog = ({ request, busy, onClose, onConfirm }: {
    request: ConfirmRequest | null
    busy: boolean
    onClose: () => void
    onConfirm: (command: Command) => void
}) => (
    <Dialog open={request != null} onClose={onClose}>
        <DialogTitle>{request?.title}</DialogTitle>
        {request?.body && (
            <DialogContent>
                <DialogContentText component="div">{request.body}</DialogContentText>
            </DialogContent>
        )}
        <DialogActions>
            <Button onClick={onClose}>Cancel</Button>
            <Button
                variant="contained"
                color={request?.danger ? "error" : "primary"}
                disabled={busy || request?.disabled}
                onClick={() => {
                    if (!request) return
                    onClose()
                    onConfirm(request.command)
                }}
            >
                {request?.confirmLabel}
            </Button>
        </DialogActions>
    </Dialog>
)
