package fi.bizhop.finanssi2.game.data;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A board square. Only the fields of the square's type are set: {@code target} for {@link SquareType#MOVE_TO}; {@code group},
 * {@code price} and {@code industrial} for {@link SquareType#PROPERTY} (Pysäköintitalo has no group); {@code percent} of share
 * capital for the share crash and player dividend squares; {@code shareClass} for the bank dividend squares that pay only on shares
 * with that printed dividend percent (39 and 42).
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
        Integer shareClass,
        String group,
        Integer price,
        boolean industrial,
        boolean bondPurchase,
        // Instruction printed on the square, when it differs from the name
        String text) {
}
