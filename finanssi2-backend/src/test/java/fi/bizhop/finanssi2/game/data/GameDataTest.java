package fi.bizhop.finanssi2.game.data;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameDataTest {
    static GameData gameData;

    @BeforeAll
    static void load() throws IOException {
        gameData = new GameDataConfig().gameData();
    }

    @Test
    void testRealFilesLoad() {
        assertEquals(46, gameData.squares().size());
        assertEquals(7, gameData.groups().size());
        assertEquals(21, gameData.cards(Deck.FINANCE_NEWS).size());
        assertEquals("FL-01", gameData.cards(Deck.FINANCE_NEWS).getFirst().id());
        assertEquals(20, gameData.squares().stream().filter(square -> square.type() == SquareType.PROPERTY).count());
    }

    @Test
    void testLookups() {
        assertEquals("Ompelimo", gameData.square(3).name());
        assertEquals(21, gameData.square(2).target());
        assertTrue(gameData.square(34).mandatoryStop());
        assertTrue(gameData.square(35).headOffice());
        assertEquals("KEMIA", gameData.groupOf(26).orElseThrow().id());
        assertTrue(gameData.groupOf(5).isEmpty());
        assertEquals(List.of(18, 19), gameData.group("FINANSSIYHTYMA").properties());
        var chapters = gameData.card("FL-01").chapters();
        assertTrue(chapters.getFirst().header());
        assertTrue(chapters.get(3).italic());
        assertFalse(gameData.card("FL-01").mock());
        assertThrows(IllegalArgumentException.class, () -> gameData.square(47));
    }

    static Square square(Square s, Integer target, String group, Integer price, boolean industrial) {
        return new Square(s.number(), s.name(), s.type(), s.headOffice(), s.mandatoryStop(), target, s.percent(), group, price,
                industrial, s.bondPurchase(), s.text());
    }

    static List<Square> squaresWith(int number, UnaryOperator<Square> change) {
        return gameData.squares().stream().map(s -> s.number() == number ? change.apply(s) : s).toList();
    }

    static void assertInvalid(List<Square> squares, List<BusinessGroup> groups, List<Card> cards) {
        assertThrows(InvalidGameDataException.class, () -> new GameData(squares, groups, cards));
    }

    static void assertInvalidSquares(List<Square> squares) {
        assertInvalid(squares, gameData.groups(), gameData.cards(Deck.FINANCE_NEWS));
    }

    @Test
    void testMissingOrDuplicateSquare() {
        var missing = new ArrayList<>(gameData.squares());
        missing.remove(10);
        assertInvalidSquares(missing);

        var duplicate = new ArrayList<>(gameData.squares());
        duplicate.set(10, gameData.square(10));
        assertInvalidSquares(duplicate);

        var extra = new ArrayList<>(gameData.squares());
        extra.add(new Square(47, "Extra", SquareType.JAIL, false, false, null, null, null, null, false, false, null));
        assertInvalidSquares(extra);
    }

    @Test
    void testPropertyWithoutGroup() {
        assertInvalidSquares(squaresWith(3, s -> square(s, null, null, s.price(), false)));
    }

    @Test
    void testPropertyInWrongGroup() {
        assertInvalidSquares(squaresWith(3, s -> square(s, null, "KEMIA", s.price(), false)));
    }

    @Test
    void testGroupListsNonProperty() {
        var groups = gameData.groups().stream()
                .map(g -> g.id().equals("KASITEOLLISUUS") ? new BusinessGroup(g.id(), g.name(), g.color(), List.of(3, 4, 5, 6)) : g)
                .toList();
        assertInvalid(gameData.squares(), groups, gameData.cards(Deck.FINANCE_NEWS));
    }

    @Test
    void testPropertyMissingFromGroup() {
        var groups = gameData.groups().stream()
                .map(g -> g.id().equals("KASITEOLLISUUS") ? new BusinessGroup(g.id(), g.name(), g.color(), List.of(3, 4)) : g)
                .toList();
        assertInvalid(gameData.squares(), groups, gameData.cards(Deck.FINANCE_NEWS));
    }

    @Test
    void testMoveToMissingSquare() {
        assertInvalidSquares(squaresWith(2, s -> square(s, 47, null, null, false)));
        assertInvalidSquares(squaresWith(2, s -> square(s, null, null, null, false)));
    }

    @Test
    void testIndustrialSquares() {
        assertInvalidSquares(squaresWith(23, s -> square(s, null, s.group(), s.price(), true)));
        assertInvalidSquares(squaresWith(26, s -> square(s, null, s.group(), s.price(), false)));
    }

    @Test
    void testInvalidPrice() {
        for (var price : new Integer[] {null, 0, -500, 10_250}) {
            assertInvalidSquares(squaresWith(3, s -> square(s, null, s.group(), price, false)));
        }
    }

    @Test
    void testInvalidCards() {
        var cards = gameData.cards(Deck.FINANCE_NEWS);
        assertInvalid(gameData.squares(), gameData.groups(), cards.subList(1, cards.size()));

        var duplicateId = new ArrayList<>(cards);
        duplicateId.set(1, cards.getFirst());
        assertInvalid(gameData.squares(), gameData.groups(), duplicateId);

        var wrongDeck = new ArrayList<>(cards);
        wrongDeck.set(1, new Card("FL-02", Deck.STOCK_TIP, cards.get(1).chapters(), false));
        assertInvalid(gameData.squares(), gameData.groups(), wrongDeck);

        var badChapter = new ArrayList<>(cards);
        badChapter.set(1, new Card("FL-02", Deck.FINANCE_NEWS, List.of(new CardChapter("Text", "heading", null)), false));
        assertInvalid(gameData.squares(), gameData.groups(), badChapter);
    }
}
