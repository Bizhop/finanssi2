package fi.bizhop.finanssi2.game.data;

import java.util.List;

/** Rule values that are not printed on any card. Money is in euros; all amounts are multiples of 500. */
public final class GameConstants {
    private GameConstants() {}

    public static final int MIN_PLAYERS = 2;
    public static final int MAX_PLAYERS = 6;
    public static final int SQUARE_COUNT = 46;
    public static final int START_SQUARE = 1;
    public static final int STARTING_CASH = 75_000;

    public static final int CAR_COUNT = 6;
    public static final int CAR_PRICE = 50_000;
    public static final int CAR_SELL_BACK_PRICE = 25_000;

    public static final int LOAN_AMOUNT = 50_000;
    public static final int LOAN_INTEREST = 5_000;
    public static final int MAX_LOANS_PER_PLAYER = 3;
    public static final int LOAN_COUNT = 6;

    public static final int BANK_ENTRANCE_REWARD_PER_PIP = 5_000;

    public static final int BOND_PRICE = 500;
    public static final int BOND_COUNT = 12;
    public static final List<Integer> SMALL_DRAW_PRIZES = List.of(50_000, 25_000, 15_000);
    public static final List<Integer> GRAND_DRAW_PRIZES = List.of(100_000, 50_000, 25_000);

    // Redeeming a mortgage costs the mortgage value plus this
    public static final int MORTGAGE_REDEMPTION_PERCENT = 10;

    public static final int MIN_BROKERAGE_FEE = 20_000;
    public static final int MAX_BROKERAGE_FEE = 120_000;
    public static final int BROKERAGE_FEE_STEP = 10_000;
    public static final int BROKERAGE_FEE_TO_BANK = 30_000;

    public static final int WINNING_CASH = 1_000_000;
    public static final int WINNING_COMPLETE_GROUPS = 2;

    public static final int FINANCE_NEWS_CARD_COUNT = 21;
    // The rules list 42; one is missing from the physical set
    public static final int STOCK_TIP_CARD_COUNT = 41;
    public static final List<Integer> INDUSTRIAL_SQUARES = List.of(26, 27, 29, 30, 32, 33);
}
