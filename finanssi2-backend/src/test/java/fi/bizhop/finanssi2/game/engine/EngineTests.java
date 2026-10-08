package fi.bizhop.finanssi2.game.engine;

import fi.bizhop.finanssi2.game.data.GameData;
import fi.bizhop.finanssi2.game.data.GameDataConfig;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

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
    public static List<GameEvent> send(GameState state, String playerId, GameCommand command) {
        return ENGINE.handle(state, playerId, command, new ScriptedDice());
    }

    /** Asserts that the command is rejected and the state is unchanged */
    public static void assertRejected(Class<? extends RuntimeException> expected, GameState state, String playerId,
                                      GameCommand command) {
        var before = Snapshot.of(state);
        assertThrows(expected, () -> send(state, playerId, command));
        assertEquals(before, Snapshot.of(state));
    }

    /** Immutable values of every state field; no dependency on generated toString or equals methods. */
    record Snapshot(String currentPlayer, TurnPhase phase, List<String> turnOrder, List<PlayerSnapshot> players,
                    List<PendingDecision> pendingDecisions, GameSettings settings, List<PropertySnapshot> properties,
                    List<ShareSnapshot> shares, List<BondSnapshot> bonds, boolean boughtThisTurn, List<String> financeNewsDeck,
                    String activeFinanceNews, List<String> stockTipDeck, boolean finished, String winner,
                    List<PlayerStanding> finalStandings) {
        static Snapshot of(GameState state) {
            return new Snapshot(state.getCurrentPlayer(), state.getPhase(), List.copyOf(state.getTurnOrder()),
                    state.getPlayers().stream().map(PlayerSnapshot::of).toList(),
                    List.copyOf(state.getPendingDecisions()), state.getSettings(),
                    state.getProperties().stream().map(PropertySnapshot::of).toList(),
                    state.getShares().stream().map(ShareSnapshot::of).toList(),
                    state.getBonds().stream().map(BondSnapshot::of).toList(), state.isBoughtThisTurn(),
                    List.copyOf(state.getFinanceNewsDeck()), state.getActiveFinanceNews(), List.copyOf(state.getStockTipDeck()),
                    state.isFinished(), state.getWinner(), List.copyOf(state.getFinalStandings()));
        }
    }

    record PlayerSnapshot(String playerId, String name, String photoUrl, int piece, int cash, int position,
                          boolean car, int loans, boolean out, int missedTurns, boolean missedTurnsInJail,
                          boolean jailExemption, boolean bailRollPending,
                          boolean transportNewsDue,
                          boolean noMovementRollThisTurn,
                          List<String> heldStockTips) {
        static PlayerSnapshot of(PlayerState player) {
            return new PlayerSnapshot(player.getPlayerId(), player.getName(), player.getPhotoUrl(), player.getPiece(),
                    player.getCash(), player.getPosition(), player.isCar(), player.getLoans(), player.isOut(),
                    player.getMissedTurns(), player.isMissedTurnsInJail(), player.isJailExemption(), player.isBailRollPending(),
                    player.isTransportNewsDue(), player.isNoMovementRollThisTurn(),
                    List.copyOf(player.getHeldStockTips()));
        }
    }

    record PropertySnapshot(int square, String owner, boolean mortgaged, boolean built) {
        static PropertySnapshot of(PropertyState property) {
            return new PropertySnapshot(property.getSquare(), property.getOwner(), property.isMortgaged(), property.isBuilt());
        }
    }

    record ShareSnapshot(String id, String owner) {
        static ShareSnapshot of(ShareState share) {
            return new ShareSnapshot(share.getId(), share.getOwner());
        }
    }

    record BondSnapshot(int number, String owner) {
        static BondSnapshot of(BondState bond) {
            return new BondSnapshot(bond.getNumber(), bond.getOwner());
        }
    }
}
