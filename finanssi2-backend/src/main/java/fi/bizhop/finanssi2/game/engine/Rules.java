package fi.bizhop.finanssi2.game.engine;

import fi.bizhop.finanssi2.game.data.GameData;

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
}
