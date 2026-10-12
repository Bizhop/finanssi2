package fi.bizhop.finanssi2.game.engine;


/** The Finance News cards. Ids ({@code FL-01} …) are what the card data, saved games and the API carry. */
public enum FinanceNewsCard {
    FL_01, FL_02, FL_03, FL_04, FL_05, FL_06, FL_07, FL_08, FL_09, FL_10, FL_11, FL_12, FL_13, FL_14, FL_15, FL_16, FL_17, FL_18, FL_19, FL_20, FL_21;

    private final String id = name().replace('_', '-');

    public String id() {
        return id;
    }

    /** The card with this id; throws for an unknown one */
    public static FinanceNewsCard of(String id) {
        return valueOf(id.replace('-', '_'));
    }

    /** Whether this is the lasting card currently in effect */
    boolean isActive(GameState state) {
        return id.equals(state.getActiveFinanceNews());
    }
}
