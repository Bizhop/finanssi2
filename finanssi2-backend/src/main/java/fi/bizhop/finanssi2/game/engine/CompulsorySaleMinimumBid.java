package fi.bizhop.finanssi2.game.engine;

/** Minimum opening price for the Stock Tip compulsory sale. */
public enum CompulsorySaleMinimumBid {
    // Original rules: bidding starts from nothing, so an asset can go for a token sum
    NONE,
    // Recommended (the default): bidding starts from half the asset's nominal price
    HALF_NOMINAL_PRICE
}
