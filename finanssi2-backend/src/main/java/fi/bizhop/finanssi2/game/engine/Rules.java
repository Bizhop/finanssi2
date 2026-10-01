package fi.bizhop.finanssi2.game.engine;

import fi.bizhop.finanssi2.game.data.GameData;
import fi.bizhop.finanssi2.game.data.Share;
import fi.bizhop.finanssi2.game.data.SquareType;
import fi.bizhop.finanssi2.game.data.TitleDeed;

import java.util.List;

import static fi.bizhop.finanssi2.game.data.GameConstants.BANK_ENTRANCE_REWARD_PER_PIP;
import static fi.bizhop.finanssi2.game.data.GameConstants.LOAN_COUNT;
import static fi.bizhop.finanssi2.game.data.GameConstants.LOAN_INTEREST;
import static fi.bizhop.finanssi2.game.data.GameConstants.MAX_LOANS_PER_PLAYER;

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
        return player.isCar() && player.getPosition() < BANK_FIRST_SQUARE ? 2 : 1;
    }

    /** How many more loans the player may take, by the per-player limit and the bank's total under the game's settings */
    public int loansAvailable(GameState state, PlayerState player) {
        var available = MAX_LOANS_PER_PLAYER - player.getLoans();
        if (state.getSettings().loanLimit() == LoanLimit.OFFICIAL) {
            available = Math.min(available, LOAN_COUNT - state.totalLoans());
        }
        return Math.max(0, available);
    }

    /** Interest per loan, on square 1 and on the loan repaid on square 43 */
    public int loanInterest(GameState state) {
        return LOAN_INTEREST;
    }

    public int bankEntranceReward(GameState state, List<Integer> roll) {
        return roll.stream().mapToInt(Integer::intValue).sum() * BANK_ENTRANCE_REWARD_PER_PIP;
    }

    /** What the bank sells a property for */
    public int propertyPrice(GameState state, TitleDeed deed) {
        return deed.price();
    }

    /** What the bank sells a share for */
    public int sharePrice(GameState state, Share share) {
        return share.value();
    }

    /**
     * Rent the payer owes the owner, 0 when none is due: the deed's rent for the property's state (none where the deed shows a dash),
     * doubled for a complete group. Pysäköintitalo charges its parking fee from car owners only.
     */
    public int rent(GameState state, TitleDeed deed, PropertyState property, PlayerState payer, boolean completeGroup) {
        if (deed.parkingFee() != null) {
            return payer.isCar() ? deed.parkingFee() : 0;
        }
        var rent = deed.rent().get(property.isBuilt());
        if (rent == null) {
            return 0;
        }
        return completeGroup ? rent * 2 : rent;
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
        return deed.buyBack() == null ? null : deed.buyBack().get(property.isBuilt());
    }

    /** Whether the player may build now: while standing on a Rakennusprojekti Oy square (17 or 40) */
    public boolean canBuild(GameState state, PlayerState player) {
        return gameData.square(player.getPosition()).type() == SquareType.CONSTRUCTION;
    }

    public int buildingPrice(GameState state, TitleDeed deed) {
        return deed.building().price();
    }

    /** What the bank pays for the share */
    public int shareBuyBack(GameState state, Share share) {
        return share.buyBack();
    }
}
