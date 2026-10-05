package fi.bizhop.finanssi2.game.engine;

/** How many bank loans all players together may have; each player may have at most 3 in any case */
public enum LoanLimit {
    // Original rules: 6 loans in total, as there are 6 loan certificates
    OFFICIAL,
    // Recommended (the default): no total limit. The official limit lets the first players take all the loans in the first round, and with them
    // the money for early purchases.
    UNLIMITED
}
