package fi.bizhop.finanssi2.game.engine;

import java.util.EnumSet;
import java.util.Set;

/** The Stock Tip cards. Ids ({@code PV-01} …) are what the card data, saved games and the API carry. */
public enum StockTipCard {
    PV_01, PV_02, PV_03, PV_04, PV_05, PV_06, PV_07, PV_08, PV_09, PV_10, PV_11, PV_12, PV_13, PV_14, PV_15, PV_16, PV_17, PV_18, PV_19, PV_20, PV_21, PV_22, PV_23, PV_24, PV_25, PV_26, PV_27, PV_28, PV_29, PV_30, PV_31, PV_32, PV_33, PV_34, PV_35, PV_36, PV_37, PV_38, PV_39, PV_40, PV_41;

    private final String id = name().replace('_', '-');

    public String id() {
        return id;
    }

    /** The card with this id; throws for an unknown one */
    public static StockTipCard of(String id) {
        return valueOf(id.replace('-', '_'));
    }

    private static final Set<StockTipCard> HELD = EnumSet.of(PV_01, PV_02, PV_07, PV_08, PV_25, PV_29);
    private static final Set<StockTipCard> PURCHASE_CERTIFICATES = EnumSet.of(PV_02, PV_08);

    /** Cards the drawer keeps until used, instead of resolving them at once */
    boolean isHeld() {
        return HELD.contains(this);
    }

    boolean isPurchaseCertificate() {
        return PURCHASE_CERTIFICATES.contains(this);
    }

    boolean heldBy(PlayerState player) {
        return player.getHeldStockTips().contains(id);
    }
}
