package fi.bizhop.finanssi2.game.engine;

/** House rules, chosen by the creator in the lobby and fixed when the game starts */
public record GameSettings(LoanLimit loanLimit) {
    public static final GameSettings DEFAULT = new GameSettings(LoanLimit.OFFICIAL);
}
