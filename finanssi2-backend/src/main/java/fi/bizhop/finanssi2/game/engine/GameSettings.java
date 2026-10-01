package fi.bizhop.finanssi2.game.engine;

/** House rules, chosen by the creator in the lobby and fixed when the game starts */
public record GameSettings(LoanLimit loanLimit, CompulsorySaleMinimumBid compulsorySaleMinimumBid) {
    public static final GameSettings DEFAULT = new GameSettings(LoanLimit.OFFICIAL, CompulsorySaleMinimumBid.NONE);

    public GameSettings {
        if (compulsorySaleMinimumBid == null) compulsorySaleMinimumBid = CompulsorySaleMinimumBid.NONE;
    }

    public GameSettings(LoanLimit loanLimit) {
        this(loanLimit, CompulsorySaleMinimumBid.NONE);
    }
}
