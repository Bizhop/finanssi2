/** The Stock Tips that ask the player to choose, by what they do; the ids are the printed card numbers the backend uses */
export const STOCK_TIP = {
    FIRE: "PV-10",
    MOVE_TO_PROPERTY: "PV-17",
    FREE_BOND: "PV-24",
    TRAVEL: "PV-26",
    JAIL_OR_BAIL: "PV-30",
    SELL_AT_NOMINAL: "PV-31",
    TRANSFER_ASSET: "PV-35",
    COMPULSORY_SALE: "PV-36",
    SHARE_SWAP: "PV-38",
} as const

/** Tips whose options are single assets, shown as asset cards */
export const ASSET_PICK_TIPS: readonly string[] = [STOCK_TIP.SELL_AT_NOMINAL, STOCK_TIP.TRANSFER_ASSET, STOCK_TIP.COMPULSORY_SALE]

/** The cost of travelling to the bank entrance with Stock Tip PV-26 */
export const TRAVEL_FEE = 25_000
