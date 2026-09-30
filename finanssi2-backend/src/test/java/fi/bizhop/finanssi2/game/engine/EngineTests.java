package fi.bizhop.finanssi2.game.engine;

import fi.bizhop.finanssi2.game.data.GameData;
import fi.bizhop.finanssi2.game.data.GameDataConfig;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Shared engine and helpers for rule tests */
public final class EngineTests {
    private EngineTests() {}

    public static final GameData GAME_DATA = load();
    public static final GameEngine ENGINE = new GameEngine(GAME_DATA, new Rules(GAME_DATA));

    static GameData load() {
        try {
            return new GameDataConfig().gameData();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The current player rolls; fails if the dice are not used up */
    public static List<GameEvent> roll(GameState state, Integer... dice) {
        var scripted = new ScriptedDice(dice);
        var events = ENGINE.handle(state, state.getCurrentPlayer(), new GameCommand.Roll(), scripted);
        assertTrue(scripted.isEmpty(), "dice left over");
        return events;
    }

    /** A command that rolls no dice */
    public static List<GameEvent> send(GameState state, String uid, GameCommand command) {
        return ENGINE.handle(state, uid, command, new ScriptedDice());
    }

    /** Asserts that the command is rejected and the state is unchanged */
    public static void assertRejected(Class<? extends RuntimeException> expected, GameState state, String uid,
                                      GameCommand command) {
        var before = Snapshot.of(state);
        assertThrows(expected, () -> send(state, uid, command));
        assertEquals(before, Snapshot.of(state));
    }

    /** A deep copy of the state, for checking that a rejected command changed nothing */
    record Snapshot(String currentPlayer, TurnPhase phase, List<String> turnOrder, Set<String> players,
                    List<PendingDecision> pendingDecisions, GameSettings settings) {
        static Snapshot of(GameState state) {
            return new Snapshot(state.getCurrentPlayer(), state.getPhase(), List.copyOf(state.getTurnOrder()),
                    state.getPlayers().stream().map(PlayerState::toString).collect(Collectors.toSet()),
                    List.copyOf(state.getPendingDecisions()), state.getSettings());
        }
    }
}
