package fi.bizhop.finanssi2.game.engine;

import fi.bizhop.finanssi2.game.data.GameData;
import fi.bizhop.finanssi2.game.data.Share;
import fi.bizhop.finanssi2.game.data.Square;
import fi.bizhop.finanssi2.game.data.SquareType;
import fi.bizhop.finanssi2.game.data.TitleDeed;

import java.util.List;

import static fi.bizhop.finanssi2.game.data.GameConstants.BANK_ENTRANCE_REWARD_PER_PIP;
import static fi.bizhop.finanssi2.game.data.GameConstants.BOND_PRICE;
import static fi.bizhop.finanssi2.game.data.GameConstants.GRAND_DRAW_PRIZES;
import static fi.bizhop.finanssi2.game.data.GameConstants.LOAN_COUNT;
import static fi.bizhop.finanssi2.game.data.GameConstants.LOAN_INTEREST;
import static fi.bizhop.finanssi2.game.data.GameConstants.MAX_LOANS_PER_PLAYER;
import static fi.bizhop.finanssi2.game.data.GameConstants.SMALL_DRAW_PRIZES;

/**
 * Rule values that cards and settings can change. Callers ask here instead of using the constants, so Finance News, Stock Tips and
 * house rules only change this class.
 */
public class Rules {
    static final int BANK_FIRST_SQUARE = 34;

    final GameData gameData;

    public Rules(GameData gameData) {
        this.gameData = gameData;
    }

    /** Number of dice for a movement roll: two with a car, one without, and one inside the bank (starting on 34–46) */
    public int movementDice(GameState state, PlayerState player) {
        if ("FL-06".equals(state.getActiveFinanceNews())) return 2;
        if ("FL-17".equals(state.getActiveFinanceNews()) && player.isCar()
                && !ownsProperty(state, player.getUid(), 27)) return 1;
        return player.isCar() && player.getPosition() < BANK_FIRST_SQUARE ? 2 : 1;
    }

    /** How many more loans the player may take, by the per-player limit and the bank's total under the game's settings */
    public int loansAvailable(GameState state, PlayerState player) {
        var perPlayer = MAX_LOANS_PER_PLAYER - player.getLoans();
        var available = switch (state.getSettings().loanLimit()) {
            case OFFICIAL -> Math.min(perPlayer, LOAN_COUNT - state.totalLoans());
            case UNLIMITED -> perPlayer;
        };
        return Math.max(0, available);
    }

    /** Interest per loan, on square 1 and on the loan repaid on square 43 */
    public int loanInterest(GameState state) {
        return "FL-01".equals(state.getActiveFinanceNews()) ? LOAN_INTEREST * 2 : LOAN_INTEREST;
    }

    public int bankEntranceReward(GameState state, List<Integer> roll) {
        var pips = "FL-06".equals(state.getActiveFinanceNews()) ? roll.stream().mapToInt(Integer::intValue).min().orElse(0)
                : roll.stream().mapToInt(Integer::intValue).sum();
        var reward = pips * BANK_ENTRANCE_REWARD_PER_PIP;
        return "FL-16".equals(state.getActiveFinanceNews()) ? reward * 2 : reward;
    }

    public int bondPrice(GameState state) {
        return BOND_PRICE;
    }

    public List<Integer> smallBondPrizes(GameState state) {
        return SMALL_DRAW_PRIZES;
    }

    public List<Integer> grandBondPrizes(GameState state) {
        return GRAND_DRAW_PRIZES;
    }

    /** What the bank sells a property for */
    public int propertyPrice(GameState state, TitleDeed deed) {
        var price = deed.price();
        if ("FL-11".equals(state.getActiveFinanceNews()) && !isBuiltProperty(state, deed.square())) price = price * 3 / 2;
        if ("FL-14".equals(state.getActiveFinanceNews()) && isIndustrial(deed.square())) price *= 2;
        return price;
    }

    /** What the bank sells a share for */
    public int sharePrice(GameState state, Share share) {
        if ("FL-08".equals(state.getActiveFinanceNews())) return halfPrice(share.value());
        return "FL-20".equals(state.getActiveFinanceNews()) ? share.value() * 2 : share.value();
    }

    /**
     * Rent the payer owes the owner, 0 when none is due: the deed's rent for the property's state (none where the deed shows a dash),
     * doubled for a complete group. Pysäköintitalo charges its parking fee from car owners only.
     */
    public int rent(GameState state, TitleDeed deed, PropertyState property, PlayerState payer, boolean completeGroup) {
        if (deed.parkingFee() != null) {
            return parkingFee(state, deed.parkingFee(), payer);
        }
        var rent = deed.rent().get(property.isBuilt());
        if (rent == null) {
            return 0;
        }
        if (completeGroup) rent *= 2;
        return "FL-05".equals(state.getActiveFinanceNews()) ? rent / 2 : rent;
    }

    /** What the bank lends on the property in its current state; null when it cannot be mortgaged */
    public Integer mortgageValue(GameState state, TitleDeed deed, PropertyState property) {
        return deed.mortgage() == null ? null : deed.mortgage().get(property.isBuilt());
    }

    /** Price of paying off the mortgage, printed on the back of the deed */
    public int redemptionPrice(GameState state, TitleDeed deed, PropertyState property) {
        return deed.redemption().get(property.isBuilt());
    }

    /** What the bank pays for the property; null when the bank does not buy it back in its current state */
    public Integer propertyBuyBack(GameState state, TitleDeed deed, PropertyState property) {
        if (deed.buyBack() == null) return null;
        var value = deed.buyBack().get(property.isBuilt());
        if (value != null && !property.isBuilt() && "FL-11".equals(state.getActiveFinanceNews())) return value * 3 / 2;
        return value;
    }

    /** Whether the player may build now: while standing on a Rakennusprojekti Oy square (17 or 40) */
    public boolean canBuild(GameState state, PlayerState player) {
        var onProject = canBuildAtProject(state, player);
        var permitAtProject = onProject && (player.getHeldStockTips().contains("PV-01") || player.getHeldStockTips().contains("PV-07"));
        var permitAnywhere = player.getHeldStockTips().contains("PV-07");
        return (onProject || permitAnywhere) && (!"FL-12".equals(state.getActiveFinanceNews()) || permitAtProject || permitAnywhere);
    }

    boolean canBuildAtProject(GameState state, PlayerState player) {
        return gameData.square(player.getPosition()).type() == SquareType.CONSTRUCTION;
    }

    public int buildingPrice(GameState state, TitleDeed deed) {
        return "FL-14".equals(state.getActiveFinanceNews()) ? deed.building().price() * 2 : deed.building().price();
    }

    /** What the bank pays for the share */
    public int shareBuyBack(GameState state, Share share) {
        if ("FL-06".equals(state.getActiveFinanceNews()) || "FL-08".equals(state.getActiveFinanceNews())
                || "FL-09".equals(state.getActiveFinanceNews())) return halfPrice(share.buyBack());
        return "FL-20".equals(state.getActiveFinanceNews()) ? share.buyBack() * 2 : share.buyBack();
    }

    /** The bank's dividend on a share (squares 16, 28, 39, 42 and 46): the dividend printed on it */
    public int bankDividend(GameState state, Share share) {
        if (dividendsStopped(state)) return 0;
        var dividend = share.dividend();
        return "FL-16".equals(state.getActiveFinanceNews()) || "FL-20".equals(state.getActiveFinanceNews()) ? dividend * 2 : dividend;
    }

    /** Square 41: the dividend owed to another player, the square's percent of that player's share capital in the counted groups */
    public int playerDividend(GameState state, Square square, int shareCapital) {
        if (dividendsStopped(state)) return 0;
        var dividend = shareCapital * square.percent() / 100;
        return "FL-16".equals(state.getActiveFinanceNews()) ? dividend * 2 : dividend;
    }

    /** Square 35: the square's percent of the share capital outside complete groups, to the bank */
    public int shareCrash(GameState state, Square square, int shareCapital) {
        return shareCapital * square.percent() / 100;
    }

    int parkingFee(GameState state, int amount, PlayerState payer) {
        if ("FL-17".equals(state.getActiveFinanceNews())) return 0;
        return payer.isCar() ? amount : 0;
    }

    boolean shareTradingStopped(GameState state) { return "FL-09".equals(state.getActiveFinanceNews()); }
    boolean propertiesTradingStopped(GameState state) { return "FL-21".equals(state.getActiveFinanceNews()); }
    boolean loansStopped(GameState state) { return "FL-01".equals(state.getActiveFinanceNews()); }
    boolean dividendsStopped(GameState state) { return "FL-06".equals(state.getActiveFinanceNews()) || "FL-08".equals(state.getActiveFinanceNews()); }
    public boolean shareholdersMeetingsAllowed(GameState state) { return !"FL-15".equals(state.getActiveFinanceNews()); }

    public int compulsorySaleMinimumBid(GameState state, String asset) {
        if (state.getSettings().compulsorySaleMinimumBid() != CompulsorySaleMinimumBid.HALF_NOMINAL_PRICE) return 0;
        var parts = asset.split(":");
        if (parts[0].equals("S")) return gameData.share(parts[1]).value() / 2;
        var property = state.property(Integer.parseInt(parts[1]));
        var deed = gameData.titleDeed(property.getSquare());
        return (deed.price() + (property.isBuilt() ? deed.building().price() : 0)) / 2;
    }

    private int halfPrice(int value) {
        return ((value + 999) / 1_000) * 500;
    }
    private boolean isIndustrial(int square) { return List.of(26, 27, 29, 30, 32, 33).contains(square); }
    private boolean isBuiltProperty(GameState state, int square) { return state.getProperties().stream().anyMatch(p -> p.getSquare() == square && p.isBuilt()); }
    private boolean ownsProperty(GameState state, String uid, int square) { return state.getProperties().stream().anyMatch(p -> p.getSquare() == square && uid.equals(p.getOwner())); }
}
