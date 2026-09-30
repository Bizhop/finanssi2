package fi.bizhop.finanssi2.game.data;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

import static fi.bizhop.finanssi2.game.data.GameConstants.FINANCE_NEWS_CARD_COUNT;
import static fi.bizhop.finanssi2.game.data.GameConstants.INDUSTRIAL_SQUARES;
import static fi.bizhop.finanssi2.game.data.GameConstants.SQUARE_COUNT;

/**
 * Static game assets, checked for consistency on construction. Title deeds, shares and Stock Tips are added once they are
 * transcribed from the physical game.
 */
public final class GameData {
    static final int MONEY_UNIT = 500;

    final Map<Integer, Square> squares;
    final Map<String, BusinessGroup> groups;
    final Map<String, Card> cards;

    public GameData(List<Square> squares, List<BusinessGroup> groups, List<Card> financeNews) {
        validateSquares(squares);
        this.squares = indexUnique(squares, Square::number, "square");
        this.groups = indexUnique(groups, BusinessGroup::id, "group");
        validateGroups();
        this.cards = indexUnique(financeNews, Card::id, "card");
        validateCards(financeNews, Deck.FINANCE_NEWS, FINANCE_NEWS_CARD_COUNT);
    }

    /** Squares 1–46 in board order */
    public List<Square> squares() {
        return List.copyOf(squares.values());
    }

    public Square square(int number) {
        var square = squares.get(number);
        if (square == null) {
            throw new IllegalArgumentException("No square " + number);
        }
        return square;
    }

    public List<BusinessGroup> groups() {
        return List.copyOf(groups.values());
    }

    public BusinessGroup group(String id) {
        var group = groups.get(id);
        if (group == null) {
            throw new IllegalArgumentException("No group " + id);
        }
        return group;
    }

    /** Group of a property square; empty for other squares */
    public Optional<BusinessGroup> groupOf(int square) {
        return Optional.ofNullable(square(square).group()).map(groups::get);
    }

    /** Cards of a deck in file order */
    public List<Card> cards(Deck deck) {
        return cards.values().stream().filter(card -> card.deck() == deck).toList();
    }

    public Card card(String id) {
        var card = cards.get(id);
        if (card == null) {
            throw new IllegalArgumentException("No card " + id);
        }
        return card;
    }

    static <T, K> Map<K, T> indexUnique(List<T> items, Function<T, K> key, String what) {
        var index = new LinkedHashMap<K, T>();
        for (var item : items) {
            if (index.put(key.apply(item), item) != null) {
                throw new InvalidGameDataException("Duplicate " + what + " " + key.apply(item));
            }
        }
        return index;
    }

    static void validateSquares(List<Square> squares) {
        var numbers = squares.stream().map(Square::number).collect(Collectors.toCollection(TreeSet::new));
        for (int number = 1; number <= SQUARE_COUNT; number++) {
            if (!numbers.contains(number)) {
                throw new InvalidGameDataException("Square " + number + " is missing");
            }
        }
        if (squares.size() != SQUARE_COUNT) {
            throw new InvalidGameDataException("Expected " + SQUARE_COUNT + " squares, got " + squares.size());
        }
        var industrial = new ArrayList<Integer>();
        for (var square : squares) {
            if (square.type() == null || square.name() == null) {
                throw new InvalidGameDataException("Square " + square.number() + " has no type or name");
            }
            if (square.type() == SquareType.MOVE_TO && (square.target() == null || !numbers.contains(square.target()))) {
                throw new InvalidGameDataException("Square " + square.number() + " moves to a square that does not exist");
            }
            if (square.type() == SquareType.PROPERTY) {
                if (square.group() == null) {
                    throw new InvalidGameDataException("Property " + square.number() + " has no group");
                }
                if (!isMoney(square.price())) {
                    throw new InvalidGameDataException("Property " + square.number() + " has an invalid price " + square.price());
                }
            } else if (square.group() != null || square.price() != null) {
                throw new InvalidGameDataException("Square " + square.number() + " is not a property but has a group or price");
            }
            if (square.industrial()) {
                industrial.add(square.number());
            }
        }
        if (!industrial.equals(INDUSTRIAL_SQUARES)) {
            throw new InvalidGameDataException("Industrial squares must be " + INDUSTRIAL_SQUARES + ", got " + industrial);
        }
    }

    void validateGroups() {
        var listed = new HashSet<Integer>();
        for (var group : groups.values()) {
            for (var number : group.properties()) {
                var square = squares.get(number);
                if (square == null || square.type() != SquareType.PROPERTY) {
                    throw new InvalidGameDataException("Group " + group.id() + " lists square " + number + ", which is not a property");
                }
                if (!group.id().equals(square.group())) {
                    throw new InvalidGameDataException("Group " + group.id() + " lists square " + number + " of group " + square.group());
                }
                if (!listed.add(number)) {
                    throw new InvalidGameDataException("Square " + number + " is listed in more than one group");
                }
            }
        }
        for (var square : squares.values()) {
            if (square.type() == SquareType.PROPERTY && !listed.contains(square.number())) {
                throw new InvalidGameDataException("Property " + square.number() + " is not listed in group " + square.group());
            }
        }
    }

    static void validateCards(List<Card> cards, Deck deck, int count) {
        if (cards.size() != count) {
            throw new InvalidGameDataException("Expected " + count + " " + deck + " cards, got " + cards.size());
        }
        for (var card : cards) {
            if (card.deck() != deck) {
                throw new InvalidGameDataException("Card " + card.id() + " is not a " + deck + " card");
            }
            if (card.chapters().isEmpty()) {
                throw new InvalidGameDataException("Card " + card.id() + " has no text");
            }
            for (var chapter : card.chapters()) {
                if (chapter.text() == null || chapter.text().isBlank()
                        || (chapter.type() != null && !chapter.header())
                        || (chapter.fontStyle() != null && !chapter.italic())) {
                    throw new InvalidGameDataException("Card " + card.id() + " has an invalid chapter " + chapter);
                }
            }
        }
    }

    static boolean isMoney(Integer amount) {
        return amount != null && amount > 0 && amount % MONEY_UNIT == 0;
    }
}
