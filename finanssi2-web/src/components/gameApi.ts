import type { User } from "firebase/auth"

export type GamePlayer = {
    playerId: string
    name: string
    photoUrl: string | null
    piece: number
    cash: number
    position: number
    car: boolean
    loans: number
    out: boolean
    heldStockTips: string[]
}

export type Game = {
    id: string
    mode: "NORMAL" | "DEBUG"
    status: "LOBBY" | "RUNNING" | "FINISHED"
    creator: string
    createdAt: number
    version: number | null
    state: {
        players: GamePlayer[]
        turnOrder: string[]
        currentPlayer: string | null
        settings: {
            loanLimit: "OFFICIAL" | "UNLIMITED"
            compulsorySaleMinimumBid: "NONE" | "HALF_NOMINAL_PRICE"
            /** House rule ALL_ASSETS_BOUGHT (the default): a meeting needs every asset of the group bought from the bank */
            shareholdersMeeting: "ALL_ASSETS_BOUGHT" | "ANY_OTHER_OWNER"
        }
        activeFinanceNews: string | null
        finished: boolean
        winner: string | null
        phase: "BEFORE_ROLL" | "AFTER_ROLL" | null
        /** A property or share has been bought this turn; only one purchase per turn is allowed */
        boughtThisTurn?: boolean
        finalStandings: { player: string; cash: number; netWorth: number; completeGroups: string[] }[]
        properties: { square: number; owner: string | null; mortgaged: boolean; built: boolean }[]
        shares: { id: string; owner: string | null }[]
        bonds: { number: number; owner: string | null }[]
        pendingDecisions: { type: string; player: string; [key: string]: unknown }[]
    }
}

export type GameView = { game: Game; allowedCommands: string[]; actingPlayer: string | null }

export class GameApiError extends Error {
    constructor(message: string, public status: number) {
        super(message)
    }
}
export type GameSquare = {
    square: number
    name: string
    type: string
    mandatoryStop?: boolean
    group: string | null
    price: number | null
    text: string | null
}
export type GameBoardData = {
    squares: GameSquare[]
    groups: { id: string; name: string; color: string; properties: number[] }[]
    titleDeeds: {
        id: string
        square: number
        name: string
        group: string | null
        price: number
        building: { label: string; price: number } | null
        rent: { unbuilt: number | null; built: number | null } | null
        parkingFee: number | null
        mortgage: { unbuilt: number | null; built: number | null } | null
        redemption: { unbuilt: number | null; built: number | null } | null
        buyBack: { unbuilt: number | null; built: number | null } | null
    }[]
    shares: { id: string; group: string | null; value: number; dividendPercent: number; dividend: number; buyBack: number }[]
    financeNews: Card[]
    stockTips: Card[]
}
export type Card = { id: string; type: string; chapters: { type: string | null; text: string; "font-style": string | null }[] }

export async function gameApi<T>(user: User, path: string, init: RequestInit = {}): Promise<T> {
    const token = await user.getIdToken()
    const response = await fetch(`${(import.meta.env?.VITE_FINANSSI_API_URL ?? "")}${path}`, {
        ...init,
        headers: {
            Authorization: `Bearer ${token}`,
            ...(init.body && !(typeof FormData !== "undefined" && init.body instanceof FormData) ? { "Content-Type": "application/json" } : {}),
            ...init.headers,
        },
    })
    if (!response.ok) {
        const body = await response.json().catch(() => null)
        throw new GameApiError(body?.detail ?? `Request failed (${response.status})`, response.status)
    }
    if (response.status === 204) return undefined as T
    const body = await response.text()
    return body ? JSON.parse(body) as T : undefined as T
}
