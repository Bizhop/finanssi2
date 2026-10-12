// What the backend sends, checked at the boundary: a payload that does not match fails loudly in gameApi instead of leaving undefined
// fields to surface somewhere in the UI. Unknown extra fields are ignored; fields the backend may leave out get defaults.
import { z } from "zod/mini"

/** A field that is null or absent in the JSON: null in the data */
const nullable = <T extends z.ZodMiniType>(schema: T) => z._default(z.nullable(schema), null)

export const PlayerSchema = z.object({
    playerId: z.string(),
    piece: z.number(),
    cash: z.number(),
    position: z.number(),
    car: z.boolean(),
    loans: z.number(),
    out: z.boolean(),
    heldStockTips: z.array(z.string()),
    // Not from the backend: filled in from public profiles
    name: z._default(z.string(), ""),
    photoUrl: nullable(z.string()),
})
export type GamePlayer = z.infer<typeof PlayerSchema>

const ChargeSchema = z.object({ amount: z.number(), reason: z.string() })

/** Input the game waits for, by `type`; only the addressed player of the first one may act */
export const PendingDecisionSchema = z.discriminatedUnion("type", [
    z.object({ type: z.literal("RaiseFunds"), player: z.string(), creditor: nullable(z.string()), amount: z.number(), charges: z.array(ChargeSchema) }),
    z.object({ type: z.literal("BondOffer"), player: z.string(), after: z.string() }),
    z.object({ type: z.literal("BondAuction"), player: z.string(), order: z.array(z.string()), index: z.number() }),
    z.object({ type: z.literal("NewsDirection"), player: z.string(), card: z.string() }),
    z.object({ type: z.literal("StockTipChoice"), player: z.string(), card: z.string(), options: z.array(z.string()) }),
    z.object({
        type: z.literal("AssetAuction"),
        player: z.string(),
        seller: z.string(),
        asset: z.string(),
        minimumBid: z.number(),
        order: z.array(z.string()),
        index: z.number(),
    }),
])
export type PendingDecision = z.infer<typeof PendingDecisionSchema>

export const GameSchema = z.object({
    id: z.string(),
    mode: z.enum(["NORMAL", "DEBUG"]),
    status: z.enum(["LOBBY", "RUNNING", "FINISHED"]),
    creator: z.string(),
    createdAt: z.number(),
    version: nullable(z.number()),
    state: z.object({
        players: z.array(PlayerSchema),
        turnOrder: z.array(z.string()),
        currentPlayer: nullable(z.string()),
        settings: z.object({
            loanLimit: z.string(),
            compulsorySaleMinimumBid: z.string(),
            /** House rule ALL_ASSETS_BOUGHT (the default): a meeting needs every asset of the group bought from the bank */
            shareholdersMeeting: z._default(z.string(), "ALL_ASSETS_BOUGHT"),
        }),
        activeFinanceNews: nullable(z.string()),
        finished: z.boolean(),
        winner: nullable(z.string()),
        phase: nullable(z.enum(["BEFORE_ROLL", "AFTER_ROLL"])),
        /** A property or share has been bought this turn; only one purchase per turn is allowed */
        boughtThisTurn: z._default(z.boolean(), false),
        finalStandings: z.array(z.object({ player: z.string(), cash: z.number(), netWorth: z.number(), completeGroups: z.array(z.string()) })),
        properties: z.array(z.object({ square: z.number(), owner: nullable(z.string()), mortgaged: z.boolean(), built: z.boolean() })),
        shares: z.array(z.object({ id: z.string(), owner: nullable(z.string()) })),
        bonds: z.array(z.object({ number: z.number(), owner: nullable(z.string()) })),
        pendingDecisions: z.array(PendingDecisionSchema),
    }),
})
export type Game = z.infer<typeof GameSchema>

const SquareOfferSchema = z.object({ square: z.number(), amount: z.number() })
const ShareOfferSchema = z.object({ share: z.string(), amount: z.number() })

/** The concrete choices the player has right now, with what each costs or pays under the current rules (Finance News included) */
export const CommandOptionsSchema = z.object({
    buyProperty: z.array(SquareOfferSchema),
    buyShare: z.array(ShareOfferSchema),
    mortgage: z.array(SquareOfferSchema),
    redeem: z.array(SquareOfferSchema),
    sellBackProperty: z.array(SquareOfferSchema),
    sellBackShare: z.array(ShareOfferSchema),
    build: z.array(SquareOfferSchema),
    shareholdersMeeting: z.array(z.object({ group: z.string(), brokerageFees: z.array(z.number()) })),
    buyBond: z.array(z.number()),
    bondPrice: z.number(),
})
export type CommandOptions = z.infer<typeof CommandOptionsSchema>

export const GameViewSchema = z.object({
    game: GameSchema,
    allowedCommands: z.array(z.string()),
    actingPlayer: nullable(z.string()),
    options: CommandOptionsSchema,
})
export type GameView = z.infer<typeof GameViewSchema>

const ByStateSchema = z.object({ unbuilt: nullable(z.number()), built: nullable(z.number()) })

const CardSchema = z.object({
    id: z.string(),
    type: z.string(),
    chapters: z.array(z.object({ type: nullable(z.string()), text: z.string(), "font-style": nullable(z.string()) })),
})
export type Card = z.infer<typeof CardSchema>

export const GameBoardDataSchema = z.object({
    squares: z.array(z.object({
        square: z.number(),
        name: z.string(),
        type: z.string(),
        mandatoryStop: z.optional(z.boolean()),
        group: nullable(z.string()),
        price: nullable(z.number()),
        text: nullable(z.string()),
    })),
    groups: z.array(z.object({ id: z.string(), name: z.string(), color: z.string(), properties: z.array(z.number()) })),
    titleDeeds: z.array(z.object({
        id: z.string(),
        square: z.number(),
        name: z.string(),
        group: nullable(z.string()),
        price: z.number(),
        building: nullable(z.object({ label: z.string(), price: z.number() })),
        rent: nullable(ByStateSchema),
        parkingFee: nullable(z.number()),
        mortgage: nullable(ByStateSchema),
        redemption: nullable(ByStateSchema),
        buyBack: nullable(ByStateSchema),
    })),
    shares: z.array(z.object({
        id: z.string(),
        group: nullable(z.string()),
        value: z.number(),
        dividendPercent: z.number(),
        dividend: z.number(),
        buyBack: z.number(),
    })),
    financeNews: z.array(CardSchema),
    stockTips: z.array(CardSchema),
})
export type GameBoardData = z.infer<typeof GameBoardDataSchema>
export type GameSquare = GameBoardData["squares"][number]

export const UserProfileSchema = z.object({
    id: z.string(),
    email: z.string(),
    displayName: z.string(),
    avatar: nullable(z.string()),
    avatarSource: nullable(z.enum(["custom", "provider"])),
    version: z.number(),
    capabilities: z.object({ debugMode: z.boolean() }),
})
export type UserProfile = z.infer<typeof UserProfileSchema>

export const PublicProfileSchema = z.object({ id: z.string(), displayName: z.string(), avatar: nullable(z.string()) })
export type PublicProfile = z.infer<typeof PublicProfileSchema>
