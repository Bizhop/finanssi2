package fi.bizhop.finanssi2.game.engine;

import java.util.List;

/**
 * The concrete choices a player has right now, with what each costs or pays under the current rules (Finance News modifiers
 * included), so clients can offer exactly the legal ones without repeating the rules. Each list holds only choices that
 * {@code allowedCommands} would accept.
 *
 * @param buyProperty         properties the bank sells now, at its current price
 * @param buyShare            shares the bank sells now, at its current price
 * @param mortgage            properties that can be mortgaged, with what the bank lends
 * @param redeem              mortgaged properties that can be redeemed, with the redemption price
 * @param sellBackProperty    properties the bank buys back, with what it pays
 * @param sellBackShare       shares the bank buys back, with what it pays
 * @param build               properties that can be built on, with the building price
 * @param shareholdersMeeting groups a meeting can be called for, with the brokerage fees that are legal for the caller
 * @param buyBond             bond numbers that can be bought
 * @param bondPrice           the price of one bond
 */
public record CommandOptions(
        List<SquareOffer> buyProperty,
        List<ShareOffer> buyShare,
        List<SquareOffer> mortgage,
        List<SquareOffer> redeem,
        List<SquareOffer> sellBackProperty,
        List<ShareOffer> sellBackShare,
        List<SquareOffer> build,
        List<MeetingOffer> shareholdersMeeting,
        List<Integer> buyBond,
        int bondPrice) {
    public static final CommandOptions NONE =
            new CommandOptions(List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), 0);

    /** A property and the euro amount that goes with the choice */
    public record SquareOffer(int square, int amount) {}

    /** A share and the euro amount that goes with the choice */
    public record ShareOffer(String share, int amount) {}

    public record MeetingOffer(String group, List<Integer> brokerageFees) {}
}
