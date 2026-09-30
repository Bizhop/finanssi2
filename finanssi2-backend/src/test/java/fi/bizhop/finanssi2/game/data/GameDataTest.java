package fi.bizhop.finanssi2.game.data;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameDataTest {
    static GameAssets assets;
    static GameData gameData;

    @BeforeAll
    static void load() throws IOException {
        assets = GameDataConfig.readAssets();
        gameData = new GameData(assets);
    }

    @Test
    void testRealFilesLoad() {
        assertEquals(46, gameData.squares().size());
        assertEquals(7, gameData.groups().size());
        assertEquals(20, gameData.titleDeeds().size());
        assertEquals(21, gameData.shares().size());
        assertEquals(21, gameData.cards(Deck.FINANCE_NEWS).size());
        assertEquals(41, gameData.cards(Deck.STOCK_TIP).size());
        assertEquals("FL-01", gameData.cards(Deck.FINANCE_NEWS).getFirst().id());
        assertEquals("PV-41", gameData.cards(Deck.STOCK_TIP).getLast().id());
    }

    @Test
    void testBoardLookups() {
        assertEquals("Ompelimo", gameData.square(3).name());
        assertEquals(21, gameData.square(2).target());
        assertTrue(gameData.square(34).mandatoryStop());
        assertTrue(gameData.square(35).headOffice());
        assertEquals(40, gameData.square(39).shareClass());
        assertEquals(30, gameData.square(42).shareClass());
        assertEquals(20, gameData.square(41).percent());
        assertEquals("KEMIA", gameData.groupOf(26).orElseThrow().id());
        assertTrue(gameData.groupOf(5).isEmpty());
        assertTrue(gameData.groupOf(8).isEmpty());
        assertEquals(List.of(9, 10), gameData.group("PALVELUYHTIO").properties());
        assertThrows(IllegalArgumentException.class, () -> gameData.square(47));
    }

    @Test
    void testTitleDeedLookups() {
        var hotelli = gameData.titleDeed(9);
        assertNull(hotelli.rent().unbuilt());
        assertEquals(50_000, hotelli.rent().get(true));
        assertEquals(44_000, hotelli.redemption().built());
        var parking = gameData.titleDeed(8);
        assertEquals("Pysäköintitalo", parking.name());
        assertEquals(10_000, parking.parkingFee());
        assertNull(parking.mortgage());
        assertTrue(gameData.titleDeed(30).building().industrial());
        assertNull(gameData.titleDeed(30).mortgage());
        assertNull(gameData.titleDeed(3).buyBack());
        assertThrows(IllegalArgumentException.class, () -> gameData.titleDeed(5));
    }

    @Test
    void testShareLookups() {
        assertEquals(3, gameData.sharesOf("KEMIA").size());
        assertEquals(List.of("OS-RAHASTO-20", "OS-RAHASTO-25"), gameData.fundShares().stream().map(Share::id).toList());
        var share = gameData.share("OS-FINANSSIYHTYMA-2");
        assertEquals(75_000, share.value());
        assertEquals(40, share.dividendPercent());
        assertEquals(30_000, share.dividend());
        assertEquals(37_500, share.buyBack());
        assertFalse(share.fund());
    }

    @Test
    void testCardLookups() {
        var chapters = gameData.card("FL-01").chapters();
        assertTrue(chapters.getFirst().header());
        assertTrue(chapters.get(3).italic());
        assertEquals("Rakennuslupa", gameData.card("PV-01").chapters().getFirst().text());
        assertEquals(Deck.STOCK_TIP, gameData.card("PV-01").deck());
    }

    // Copies of the real assets with one part replaced

    static GameAssets withSquares(List<Square> squares) {
        return new GameAssets(squares, assets.groups(), assets.titleDeeds(), assets.shares(), assets.groupShareCapital(),
                assets.financeNews(), assets.stockTips());
    }

    static GameAssets withGroups(List<BusinessGroup> groups) {
        return new GameAssets(assets.squares(), groups, assets.titleDeeds(), assets.shares(), assets.groupShareCapital(),
                assets.financeNews(), assets.stockTips());
    }

    static GameAssets withTitleDeeds(List<TitleDeed> titleDeeds) {
        return new GameAssets(assets.squares(), assets.groups(), titleDeeds, assets.shares(), assets.groupShareCapital(),
                assets.financeNews(), assets.stockTips());
    }

    static GameAssets withShares(List<Share> shares) {
        return new GameAssets(assets.squares(), assets.groups(), assets.titleDeeds(), shares, assets.groupShareCapital(),
                assets.financeNews(), assets.stockTips());
    }

    static GameAssets withCards(List<Card> financeNews, List<Card> stockTips) {
        return new GameAssets(assets.squares(), assets.groups(), assets.titleDeeds(), assets.shares(), assets.groupShareCapital(),
                financeNews, stockTips);
    }

    static <T> List<T> replace(List<T> items, int index, UnaryOperator<T> change) {
        var copy = new ArrayList<>(items);
        copy.set(index, change.apply(copy.get(index)));
        return copy;
    }

    static List<Square> squaresWith(int number, UnaryOperator<Square> change) {
        return replace(assets.squares(), number - 1, change);
    }

    static Square square(Square s, Integer target, String group, Integer price, boolean industrial) {
        return new Square(s.number(), s.name(), s.type(), s.headOffice(), s.mandatoryStop(), target, s.percent(), s.shareClass(),
                group, price, industrial, s.bondPurchase(), s.text());
    }

    /** Index of a square's deed in the deed list */
    static int deed(int square) {
        for (int i = 0; i < assets.titleDeeds().size(); i++) {
            if (assets.titleDeeds().get(i).square() == square) {
                return i;
            }
        }
        throw new IllegalArgumentException("No deed for " + square);
    }

    static TitleDeed deedWith(TitleDeed d, Building building, ByState rent, Integer parkingFee, ByState mortgage, ByState redemption) {
        return new TitleDeed(d.id(), d.square(), d.name(), d.group(), d.price(), building, rent, parkingFee, mortgage, redemption,
                d.buyBack());
    }

    static void assertInvalid(GameAssets broken) {
        assertThrows(InvalidGameDataException.class, () -> new GameData(broken));
    }

    @Test
    void testMissingOrDuplicateSquare() {
        var missing = new ArrayList<>(assets.squares());
        missing.remove(10);
        assertInvalid(withSquares(missing));

        assertInvalid(withSquares(replace(assets.squares(), 10, s -> gameData.square(10))));

        var extra = new ArrayList<>(assets.squares());
        extra.add(new Square(47, "Extra", SquareType.JAIL, false, false, null, null, null, null, null, false, false, null));
        assertInvalid(withSquares(extra));
    }

    @Test
    void testPropertyGroups() {
        // Leaving a listed property without a group, or putting it in another group
        assertInvalid(withSquares(squaresWith(3, s -> square(s, null, null, s.price(), false))));
        assertInvalid(withSquares(squaresWith(3, s -> square(s, null, "KEMIA", s.price(), false))));

        var nonProperty = replace(assets.groups(), 0, g -> new BusinessGroup(g.id(), g.name(), g.color(), List.of(3, 4, 5, 6)));
        assertInvalid(withGroups(nonProperty));
        var missingProperty = replace(assets.groups(), 0, g -> new BusinessGroup(g.id(), g.name(), g.color(), List.of(3, 4)));
        assertInvalid(withGroups(missingProperty));
    }

    @Test
    void testMoveToMissingSquare() {
        assertInvalid(withSquares(squaresWith(2, s -> square(s, 47, null, null, false))));
        assertInvalid(withSquares(squaresWith(2, s -> square(s, null, null, null, false))));
    }

    @Test
    void testIndustrialSquares() {
        assertInvalid(withSquares(squaresWith(23, s -> square(s, null, s.group(), s.price(), true))));
        assertInvalid(withSquares(squaresWith(26, s -> square(s, null, s.group(), s.price(), false))));
    }

    @Test
    void testInvalidPrice() {
        for (var price : new Integer[] {null, 0, -500, 10_250}) {
            assertInvalid(withSquares(squaresWith(3, s -> square(s, null, s.group(), price, false))));
        }
    }

    @Test
    void testTitleDeedPerProperty() {
        var missing = new ArrayList<>(assets.titleDeeds());
        missing.remove(0);
        assertInvalid(withTitleDeeds(missing));

        var duplicate = new ArrayList<>(assets.titleDeeds());
        duplicate.add(assets.titleDeeds().getFirst());
        assertInvalid(withTitleDeeds(duplicate));

        // A deed whose price differs from the board
        assertInvalid(withTitleDeeds(replace(assets.titleDeeds(), deed(3), d -> new TitleDeed(d.id(), d.square(), d.name(), d.group(),
                d.price() + 500, d.building(), d.rent(), d.parkingFee(), d.mortgage(), d.redemption(), d.buyBack()))));
    }

    @Test
    void testParkingAndGroupDeeds() {
        // Pysäköintitalo with a building, or a group property with a parking fee
        assertInvalid(withTitleDeeds(replace(assets.titleDeeds(), deed(8),
                d -> deedWith(d, new Building("Rakennus", 10_000), d.rent(), d.parkingFee(), d.mortgage(), d.redemption()))));
        assertInvalid(withTitleDeeds(replace(assets.titleDeeds(), deed(3),
                d -> deedWith(d, d.building(), d.rent(), 10_000, d.mortgage(), d.redemption()))));
        // An industrial building on a non-industrial square, and the reverse
        assertInvalid(withTitleDeeds(replace(assets.titleDeeds(), deed(3),
                d -> deedWith(d, new Building("Teollisuus", 10_000), d.rent(), null, d.mortgage(), d.redemption()))));
        assertInvalid(withTitleDeeds(replace(assets.titleDeeds(), deed(26),
                d -> deedWith(d, new Building("Rakennus", 40_000), d.rent(), null, d.mortgage(), d.redemption()))));
    }

    @Test
    void testRedemptionIsMortgagePlusTenPercent() {
        assertInvalid(withTitleDeeds(replace(assets.titleDeeds(), deed(3),
                d -> deedWith(d, d.building(), d.rent(), null, d.mortgage(), new ByState(11_000, 22_500)))));
        // A redemption price for a state that cannot be mortgaged
        assertInvalid(withTitleDeeds(replace(assets.titleDeeds(), deed(12),
                d -> deedWith(d, d.building(), d.rent(), null, d.mortgage(), new ByState(22_000, 22_000)))));
        assertInvalid(withTitleDeeds(replace(assets.titleDeeds(), deed(3),
                d -> deedWith(d, d.building(), d.rent(), null, d.mortgage(), null))));
    }

    @Test
    void testDeedMoneyValues() {
        assertInvalid(withTitleDeeds(replace(assets.titleDeeds(), deed(3),
                d -> deedWith(d, d.building(), new ByState(2_600, 25_000), null, d.mortgage(), d.redemption()))));
        assertInvalid(withTitleDeeds(replace(assets.titleDeeds(), deed(3),
                d -> deedWith(d, d.building(), new ByState(null, null), null, d.mortgage(), d.redemption()))));
    }

    @Test
    void testShares() {
        var shares = assets.shares();
        // One share too few in a group, a share of an unknown group, and group shares that don't sum to the printed capital
        assertInvalid(withShares(shares.subList(1, shares.size())));
        assertInvalid(withShares(replace(shares, 0, s -> new Share(s.id(), "NOPE", s.value(), s.dividendPercent(), s.dividend(),
                s.buyBack()))));
        assertInvalid(withShares(replace(shares, 0, s -> new Share(s.id(), s.group(), 75_000, 20, 15_000, s.buyBack()))));
        // Dividend not matching value × percent
        assertInvalid(withShares(replace(shares, 0, s -> new Share(s.id(), s.group(), s.value(), s.dividendPercent(), 12_500,
                s.buyBack()))));
        // A third fund share
        var extraFund = new ArrayList<>(shares);
        extraFund.add(new Share("OS-RAHASTO-30", null, 50_000, 30, 15_000, 25_000));
        assertInvalid(withShares(extraFund));

        var capital = new HashMap<>(assets.groupShareCapital());
        capital.remove("KEMIA");
        assertInvalid(new GameAssets(assets.squares(), assets.groups(), assets.titleDeeds(), shares, capital, assets.financeNews(),
                assets.stockTips()));
    }

    @Test
    void testInvalidCards() {
        var news = assets.financeNews();
        var tips = assets.stockTips();
        assertInvalid(withCards(news.subList(1, news.size()), tips));
        assertInvalid(withCards(news, tips.subList(1, tips.size())));
        assertInvalid(withCards(replace(news, 1, c -> news.getFirst()), tips));
        // The same id in both decks
        assertInvalid(withCards(news, replace(tips, 0, c -> new Card("FL-01", Deck.STOCK_TIP, c.chapters()))));
        assertInvalid(withCards(replace(news, 1, c -> new Card(c.id(), Deck.STOCK_TIP, c.chapters())), tips));
        assertInvalid(withCards(replace(news, 1, c -> new Card(c.id(), c.deck(), List.of(new CardChapter("Text", "heading", null)))),
                tips));
    }
}
