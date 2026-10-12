package fi.bizhop.finanssi2.game.engine;

import fi.bizhop.finanssi2.game.data.Deck;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CardEnumTest {
    @Test
    void financeNewsCardsAreExactlyTheCardsInTheGameData() {
        var ids = EngineTests.GAME_DATA.cards(Deck.FINANCE_NEWS).stream().map(card -> card.id()).collect(Collectors.toSet());
        assertEquals(ids, Arrays.stream(FinanceNewsCard.values()).map(FinanceNewsCard::id).collect(Collectors.toSet()));
    }

    @Test
    void stockTipCardsAreExactlyTheCardsInTheGameData() {
        var ids = EngineTests.GAME_DATA.cards(Deck.STOCK_TIP).stream().map(card -> card.id()).collect(Collectors.toSet());
        assertEquals(ids, Arrays.stream(StockTipCard.values()).map(StockTipCard::id).collect(Collectors.toSet()));
    }

    @Test
    void idsRoundTripAndUnknownIdsAreRejected() {
        assertEquals(FinanceNewsCard.FL_07, FinanceNewsCard.of("FL-07"));
        assertEquals("PV-41", StockTipCard.PV_41.id());
        assertEquals(Set.of(StockTipCard.PV_01, StockTipCard.PV_02, StockTipCard.PV_07, StockTipCard.PV_08, StockTipCard.PV_25, StockTipCard.PV_29),
                Arrays.stream(StockTipCard.values()).filter(StockTipCard::isHeld).collect(Collectors.toSet()));
        assertThrows(IllegalArgumentException.class, () -> StockTipCard.of("PV-99"));
        assertThrows(IllegalArgumentException.class, () -> FinanceNewsCard.of("FL-00"));
    }
}
