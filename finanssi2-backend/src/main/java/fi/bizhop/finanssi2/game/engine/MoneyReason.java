package fi.bizhop.finanssi2.game.engine;

/** Why money changed hands */
public enum MoneyReason {
    CAR_PURCHASE,
    CAR_SALE,
    LOAN,
    LOAN_REPAYMENT,
    LOAN_INTEREST,
    BANK_ENTRANCE_REWARD,
    // What a bankrupt player had, to their creditor
    BANKRUPTCY
}
