package fi.bizhop.finanssi2.game.data;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static fi.bizhop.finanssi2.game.data.GameConstants.FINANCE_NEWS_CARD_COUNT;
import static fi.bizhop.finanssi2.game.data.GameConstants.INDUSTRIAL_SQUARES;
import static fi.bizhop.finanssi2.game.data.GameConstants.MORTGAGE_REDEMPTION_PERCENT;
import static fi.bizhop.finanssi2.game.data.GameConstants.SQUARE_COUNT;
import static fi.bizhop.finanssi2.game.data.GameConstants.STOCK_TIP_CARD_COUNT;

/** Static game assets, checked for consistency on construction */
public final class GameData {
    static final int MONEY_UNIT = 500;
    static final int FUND_SHARE_COUNT = 2;

    final Map<Integer, Square> squares;
    final Map<String, BusinessGroup> groups;
    final Map<Integer, TitleDeed> titleDeeds;
    final Map<String, Share> shares;
    final Map<String, Card> cards;

    public GameData(GameAssets assets) {
        validateSquares(assets.squares());
        this.squares = indexUnique(assets.squares(), Square::number, "square");
        this.groups = indexUnique(assets.groups(), BusinessGroup::id, "group");
        validateGroups();
        this.titleDeeds = indexUnique(assets.titleDeeds(), TitleDeed::square, "title deed for square");
        indexUnique(assets.titleDeeds(), TitleDeed::id, "title deed");
        validateTitleDeeds();
        this.shares = indexUnique(assets.shares(), Share::id, "share");
        validateShares(assets.groupShareCapital());
        validateCards(assets.financeNews(), Deck.FINANCE_NEWS, FINANCE_NEWS_CARD_COUNT);
        validateCards(assets.stockTips(), Deck.STOCK_TIP, STOCK_TIP_CARD_COUNT);
        this.cards = indexUnique(Stream.concat(assets.financeNews().stream(), assets.stockTips().stream()).toList(), Card::id, "card");
    }

    /** Squares 1–46 in board order */
    public List<Square> squares() {
        return List.copyOf(squares.values());
    }

    public Square square(int number) {
        return require(squares.get(number), "square " + number);
    }

    public List<BusinessGroup> groups() {
        return List.copyOf(groups.values());
    }

    public BusinessGroup group(String id) {
        return require(groups.get(id), "group " + id);
    }

    /** Group of a property square; empty for other squares and for Pysäköintitalo */
    public Optional<BusinessGroup> groupOf(int square) {
        return Optional.ofNullable(square(square).group()).map(groups::get);
    }

    /** Title deeds in board order */
    public List<TitleDeed> titleDeeds() {
        return List.copyOf(titleDeeds.values());
    }

    public TitleDeed titleDeed(int square) {
        return require(titleDeeds.get(square), "title deed for square " + square);
    }

    /** All 21 shares: group shares by group, then the fund shares */
    public List<Share> shares() {
        return List.copyOf(shares.values());
    }

    public Share share(String id) {
        return require(shares.get(id), "share " + id);
    }

    public List<Share> sharesOf(String group) {
        return shares.values().stream().filter(share -> group.equals(share.group())).toList();
    }

    public List<Share> fundShares() {
        return shares.values().stream().filter(Share::fund).toList();
    }

    /** Cards of a deck in file order */
    public List<Card> cards(Deck deck) {
        return cards.values().stream().filter(card -> card.deck() == deck).toList();
    }

    public Card card(String id) {
        return require(cards.get(id), "card " + id);
    }

    static <T> T require(T value, String what) {
        if (value == null) {
            throw new IllegalArgumentException("No " + what);
        }
        return value;
    }

    static <T, K> Map<K, T> indexUnique(List<T> items, Function<T, K> key, String what) {
        var index = items.stream().collect(Collectors.toMap(key, Function.identity(), (first, duplicate) -> {
            throw new InvalidGameDataException("Duplicate " + what + " " + key.apply(duplicate));
        }, LinkedHashMap::new));
        return Collections.unmodifiableMap(index);
    }

    static void check(boolean condition, String message) {
        if (!condition) {
            throw new InvalidGameDataException(message);
        }
    }

    static void validateSquares(List<Square> squares) {
        var numbers = squares.stream().map(Square::number).collect(Collectors.toCollection(TreeSet::new));
        for (int number = 1; number <= SQUARE_COUNT; number++) {
            check(numbers.contains(number), "Square " + number + " is missing");
        }
        check(squares.size() == SQUARE_COUNT, "Expected " + SQUARE_COUNT + " squares, got " + squares.size());
        for (var square : squares) {
            check(square.type() != null && square.name() != null, "Square " + square.number() + " has no type or name");
            if (square.type() == SquareType.MOVE_TO) {
                check(square.target() != null && numbers.contains(square.target()),
                        "Square " + square.number() + " moves to a square that does not exist");
            }
            if (square.type() == SquareType.PROPERTY) {
                check(isMoney(square.price()), "Property " + square.number() + " has an invalid price " + square.price());
            } else {
                check(square.group() == null && square.price() == null,
                        "Square " + square.number() + " is not a property but has a group or price");
            }
        }
        var industrial = squares.stream().filter(Square::industrial).map(Square::number).toList();
        check(industrial.equals(INDUSTRIAL_SQUARES), "Industrial squares must be " + INDUSTRIAL_SQUARES + ", got " + industrial);
    }

    void validateGroups() {
        var listed = new HashSet<Integer>();
        for (var group : groups.values()) {
            for (var number : group.properties()) {
                var square = squares.get(number);
                check(square != null && square.type() == SquareType.PROPERTY,
                        "Group " + group.id() + " lists square " + number + ", which is not a property");
                check(group.id().equals(square.group()),
                        "Group " + group.id() + " lists square " + number + " of group " + square.group());
                check(listed.add(number), "Square " + number + " is listed in more than one group");
            }
        }
        squares.values().stream().filter(square -> square.group() != null)
                .forEach(square -> check(listed.contains(square.number()),
                        "Property " + square.number() + " is not listed in group " + square.group()));
    }

    void validateTitleDeeds() {
        for (var square : squares.values()) {
            var deed = titleDeeds.get(square.number());
            check((square.type() == SquareType.PROPERTY) == (deed != null),
                    "Square " + square.number() + (deed == null ? " has no title deed" : " is not a property but has a title deed"));
        }
        for (var deed : titleDeeds.values()) {
            var square = squares.get(deed.square());
            var at = "Title deed " + deed.id() + ": ";
            check(square.name().equals(deed.name()) && square.price().equals(deed.price()) && Objects.equals(square.group(), deed.group()),
                    at + "name, price or group differs from square " + deed.square());
            if (deed.group() == null) {
                // Pysäköintitalo: no building, a parking fee instead of rent
                check(deed.building() == null && deed.rent() == null && isMoney(deed.parkingFee()),
                        at + "a property without a group must have a parking fee and no building or rent");
            } else {
                check(deed.building() != null && deed.rent() != null && deed.parkingFee() == null,
                        at + "a group property must have a building and rent, and no parking fee");
                check(deed.building().industrial() == square.industrial(), at + "industrial building on a non-industrial square or vice versa");
                check(isMoney(deed.building().price()), at + "invalid building price");
                checkByState(deed.rent(), at + "rent");
            }
            check((deed.mortgage() == null) == (deed.redemption() == null), at + "mortgage and redemption must both exist or neither");
            if (deed.mortgage() != null) {
                checkByState(deed.mortgage(), at + "mortgage");
                checkByState(deed.redemption(), at + "redemption");
                for (var built : List.of(false, true)) {
                    var mortgage = deed.mortgage().get(built);
                    var redemption = deed.redemption().get(built);
                    check(mortgage == null ? redemption == null
                                    : redemption != null && redemption * 100 == mortgage * (100 + MORTGAGE_REDEMPTION_PERCENT),
                            at + "redemption must be the mortgage value + " + MORTGAGE_REDEMPTION_PERCENT + "%");
                }
            }
            if (deed.buyBack() != null) {
                checkByState(deed.buyBack(), at + "buy-back");
            }
        }
        var ungrouped = titleDeeds.values().stream().filter(deed -> deed.group() == null).count();
        check(ungrouped == 1, "Expected exactly one property without a group, got " + ungrouped);
    }

    /** At least one state has a value and every value is money */
    static void checkByState(ByState values, String what) {
        check(values.unbuilt() != null || values.built() != null, what + " has no values");
        Stream.of(values.unbuilt(), values.built()).filter(Objects::nonNull)
                .forEach(value -> check(isMoney(value), what + " has an invalid value " + value));
    }

    void validateShares(Map<String, Integer> groupShareCapital) {
        check(groupShareCapital.keySet().equals(groups.keySet()), "Share capital must be given for exactly the groups " + groups.keySet());
        for (var share : shares.values()) {
            check(share.group() == null || groups.containsKey(share.group()), "Share " + share.id() + " has an unknown group");
            check(isMoney(share.value()) && isMoney(share.dividend()) && isMoney(share.buyBack()), "Share " + share.id() + " has an invalid value");
            check(share.dividend() * 100 == share.value() * share.dividendPercent(),
                    "Share " + share.id() + ": dividend must be value × dividend percent");
        }
        for (var group : groups.values()) {
            var groupShares = sharesOf(group.id());
            check(groupShares.size() == group.properties().size(),
                    "Group " + group.id() + " has " + groupShares.size() + " shares for " + group.properties().size() + " properties");
            var capital = groupShares.stream().mapToInt(Share::value).sum();
            check(capital == groupShareCapital.get(group.id()),
                    "Shares of group " + group.id() + " sum to " + capital + ", not " + groupShareCapital.get(group.id()));
        }
        check(fundShares().size() == FUND_SHARE_COUNT, "Expected " + FUND_SHARE_COUNT + " fund shares, got " + fundShares().size());
    }

    static void validateCards(List<Card> cards, Deck deck, int count) {
        check(cards.size() == count, "Expected " + count + " " + deck + " cards, got " + cards.size());
        for (var card : cards) {
            check(card.deck() == deck, "Card " + card.id() + " is not a " + deck + " card");
            check(!card.chapters().isEmpty(), "Card " + card.id() + " has no text");
            for (var chapter : card.chapters()) {
                check(chapter.text() != null && !chapter.text().isBlank()
                                && (chapter.type() == null || chapter.header())
                                && (chapter.fontStyle() == null || chapter.italic()),
                        "Card " + card.id() + " has an invalid chapter " + chapter);
            }
        }
    }

    static boolean isMoney(Integer amount) {
        return amount != null && amount > 0 && amount % MONEY_UNIT == 0;
    }
}
