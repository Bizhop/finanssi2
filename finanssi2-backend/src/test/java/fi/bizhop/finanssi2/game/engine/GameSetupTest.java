package fi.bizhop.finanssi2.game.engine;

import fi.bizhop.finanssi2.game.data.GameData;
import fi.bizhop.finanssi2.game.data.GameDataConfig;
import fi.bizhop.finanssi2.game.engine.GameEvent.GameStarted;
import fi.bizhop.finanssi2.game.engine.GameEvent.StartingRoll;
import fi.bizhop.finanssi2.game.engine.GameEvent.TurnStarted;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameSetupTest {
    static GameData gameData;

    @BeforeAll
    static void load() throws IOException {
        gameData = new GameDataConfig().gameData();
    }

    static GameState lobby(String... uids) {
        var state = new GameState();
        for (int i = 0; i < uids.length; i++) {
            state.getPlayers().add(new PlayerState(uids[i], i));
        }
        return state;
    }

    @Test
    void testHighestRollStartsAndOrderFollowsJoinOrder() {
        var state = lobby("a", "b", "c");
        var dice = new ScriptedDice(1, 2, 6, 5, 3, 3);

        var events = new GameSetup(gameData).start(state, dice, new Random(1));

        assertEquals(List.of(
                new StartingRoll("a", 1, List.of(1, 2)),
                new StartingRoll("b", 1, List.of(6, 5)),
                new StartingRoll("c", 1, List.of(3, 3)),
                new GameStarted(List.of("b", "c", "a"), 75_000),
                new TurnStarted("b")), events);
        assertEquals(List.of("b", "c", "a"), state.getTurnOrder());
        assertEquals("b", state.getCurrentPlayer());
        assertEquals(TurnPhase.BEFORE_ROLL, state.getPhase());
        assertTrue(dice.isEmpty());
        for (var player : state.getPlayers()) {
            assertEquals(75_000, player.getCash());
            assertEquals(1, player.getPosition());
        }
    }

    @Test
    void testTiedHighestRollersRollAgain() {
        var state = lobby("a", "b", "c", "d");
        // a and c tie on 10 and roll again; c wins round 2
        var dice = new ScriptedDice(4, 6, 1, 1, 5, 5, 2, 2, 3, 3, 6, 1);

        var events = new GameSetup(gameData).start(state, dice, new Random(1));

        assertEquals(List.of(
                new StartingRoll("a", 1, List.of(4, 6)),
                new StartingRoll("b", 1, List.of(1, 1)),
                new StartingRoll("c", 1, List.of(5, 5)),
                new StartingRoll("d", 1, List.of(2, 2)),
                new StartingRoll("a", 2, List.of(3, 3)),
                new StartingRoll("c", 2, List.of(6, 1)),
                new GameStarted(List.of("c", "d", "a", "b"), 75_000),
                new TurnStarted("c")), events);
        assertTrue(dice.isEmpty());
    }

    @Test
    void testFinanceNewsDeckIsShuffled() {
        var state = lobby("a", "b");
        new GameSetup(gameData).start(state, new ScriptedDice(6, 6, 1, 1), new Random(1));

        var deck = state.getFinanceNewsDeck();
        var expected = IntStream.rangeClosed(1, 21).mapToObj(i -> String.format("FL-%02d", i)).toList();
        assertEquals(new HashSet<>(expected), new HashSet<>(deck));
        assertEquals(21, deck.size());
        assertTrue(!deck.equals(expected), "deck in file order");
    }

    @Test
    void testStockTipDeckIsShuffled() {
        var state = lobby("a", "b");
        new GameSetup(gameData).start(state, new ScriptedDice(6, 6, 1, 1), new Random(1));

        var deck = state.getStockTipDeck();
        var expected = IntStream.rangeClosed(1, 41).mapToObj(i -> String.format("PV-%02d", i)).toList();
        assertEquals(new HashSet<>(expected), new HashSet<>(deck));
        assertEquals(41, deck.size());
        assertTrue(!deck.equals(expected), "deck in file order");
    }

    @Test
    void testPlayerCount() {
        var setup = new GameSetup(gameData);
        var alone = lobby("a");
        assertThrows(RuleViolation.class, () -> setup.start(alone, new ScriptedDice(), new Random(1)));
        assertTrue(alone.getTurnOrder().isEmpty());
        assertEquals(0, alone.getPlayers().getFirst().getCash());

        var seven = lobby("a", "b", "c", "d", "e", "f", "g");
        assertThrows(RuleViolation.class, () -> setup.start(seven, new ScriptedDice(), new Random(1)));
    }
}
