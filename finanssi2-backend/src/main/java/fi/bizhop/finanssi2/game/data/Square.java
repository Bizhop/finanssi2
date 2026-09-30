package fi.bizhop.finanssi2.game.data;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A board square. Only the fields of the square's type are set: {@code target} for {@link SquareType#MOVE_TO}, {@code group},
 * {@code price} and {@code industrial} for {@link SquareType#PROPERTY}, {@code percent} for dividend and share crash squares
 * (null where the rules leave the amount open).
 */
public record Square(
        @JsonProperty("square") int number,
        String name,
        SquareType type,
        // Squares 35–46, inside the bank
        boolean headOffice,
        // A forward move stops here even if the roll is larger
        boolean mandatoryStop,
        Integer target,
        Integer percent,
        String group,
        Integer price,
        boolean industrial,
        boolean bondPurchase,
        // Instruction printed on the square, when it differs from the name
        String text) {
}
