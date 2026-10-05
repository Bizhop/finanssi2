package fi.bizhop.finanssi2.game.engine;

/**
 * House rules, chosen by the creator in the lobby and fixed when the game starts. The recommended options are the defaults; the
 * original printed rules stay available for legacy play.
 */
public record GameSettings(LoanLimit loanLimit, CompulsorySaleMinimumBid compulsorySaleMinimumBid, ShareholdersMeeting shareholdersMeeting) {
    /** The recommended rules */
    public static final GameSettings DEFAULT =
            new GameSettings(LoanLimit.UNLIMITED, CompulsorySaleMinimumBid.HALF_NOMINAL_PRICE, ShareholdersMeeting.ALL_ASSETS_BOUGHT);
    /** The rules as printed */
    public static final GameSettings ORIGINAL =
            new GameSettings(LoanLimit.OFFICIAL, CompulsorySaleMinimumBid.NONE, ShareholdersMeeting.ANY_OTHER_OWNER);

    // Settings saved before a setting existed get what those games were played with: no minimum bid (the only option then) and the
    // whole-group meeting rule (enforced before it became a setting)
    public GameSettings {
        if (compulsorySaleMinimumBid == null) compulsorySaleMinimumBid = CompulsorySaleMinimumBid.NONE;
        if (shareholdersMeeting == null) shareholdersMeeting = ShareholdersMeeting.ALL_ASSETS_BOUGHT;
    }

    public GameSettings(LoanLimit loanLimit) {
        this(loanLimit, CompulsorySaleMinimumBid.NONE, ShareholdersMeeting.ALL_ASSETS_BOUGHT);
    }
}
