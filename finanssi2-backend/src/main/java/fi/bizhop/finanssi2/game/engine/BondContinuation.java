package fi.bizhop.finanssi2.game.engine;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** What follows a bond purchase or pass. Codes preserve the existing API and saved decisions. */
public enum BondContinuation {
    NONE(0),
    SMALL_DRAW(1),
    GRAND_DRAW(2),
    CONTINUE_GRAND_DRAW(3);

    final int code;

    BondContinuation(int code) {
        this.code = code;
    }

    @JsonValue
    public int code() {
        return code;
    }

    @JsonCreator
    public static BondContinuation fromCode(int code) {
        return switch (code) {
            case 0 -> NONE;
            case 1 -> SMALL_DRAW;
            case 2 -> GRAND_DRAW;
            case 3 -> CONTINUE_GRAND_DRAW;
            default -> throw new IllegalArgumentException("Unknown bond continuation " + code);
        };
    }
}
