// Player actions tied to assets and their confirmations. The backend lists allowed command types only; which asset a command applies to is
// worked out here from the title deeds and shares, and the backend validates the exact command when it is sent.
import type { ReactNode } from "react"
import { Button, Dialog, DialogActions, DialogContent, DialogTitle } from "@mui/material"

import type { Game, GameBoardData } from "./gameApi.ts"

export type Command = Record<string, unknown>
export type ConfirmRequest = { title: string; body?: ReactNode; confirmLabel: string; command: Command; danger?: boolean }
export type AssetAction = { label: string; request: ConfirmRequest }

/** What the box of the player you control can do; absent for other players */
export type PlayerControls = {
    allowed: string[]
    busy: boolean
    send: (command: Command) => void
    confirm: (request: ConfirmRequest) => void
    /** Context-sensitive actions shown on their own row, e.g. buying from the bank */
    contextActions?: ReactNode
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

export const ConfirmDialog = ({ request, busy, onClose, onConfirm }: {
    request: ConfirmRequest | null
    busy: boolean
    onClose: () => void
    onConfirm: (command: Command) => void
}) => (
    <Dialog open={request != null} onClose={onClose}>
        <DialogTitle>{request?.title}</DialogTitle>
        {request?.body && <DialogContent>{request.body}</DialogContent>}
        <DialogActions>
            <Button onClick={onClose}>Cancel</Button>
            <Button
                variant="contained"
                color={request?.danger ? "error" : "primary"}
                disabled={busy}
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
