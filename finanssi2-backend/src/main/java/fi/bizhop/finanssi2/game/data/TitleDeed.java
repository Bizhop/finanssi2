package fi.bizhop.finanssi2.game.data;

/**
 * A property's title deed. {@code group}, {@code building} and {@code rent} are null for Pysäköintitalo, which charges a
 * {@code parkingFee} from car owners instead. {@code mortgage} is null when the property cannot be mortgaged ("Ei lainoitusta"),
 * {@code buyBack} when the bank does not buy it back ("Ei osteta takaisin"). {@code redemption} is printed on the back.
 */
public record TitleDeed(
        String id,
        int square,
        String name,
        String group,
        int price,
        Building building,
        ByState rent,
        Integer parkingFee,
        ByState mortgage,
        ByState redemption,
        ByState buyBack) {
}
