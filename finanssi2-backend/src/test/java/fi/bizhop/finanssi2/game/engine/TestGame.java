package fi.bizhop.finanssi2.game.engine;

import java.util.List;
import java.util.function.Consumer;

import static fi.bizhop.finanssi2.game.data.GameConstants.STARTING_CASH;

/** Builds game states for rule tests: the given players in turn order, each with starting cash on square 1, the first to roll */
public class TestGame {
    final GameState state = new GameState();

    public static TestGame players(String... uids) {
        var game = new TestGame();
        for (int i = 0; i < uids.length; i++) {
            var player = new PlayerState(uids[i], "Player " + uids[i], null, i);
            player.setCash(STARTING_CASH);
            player.setPosition(1);
            game.state.getPlayers().add(player);
        }
        game.state.setTurnOrder(List.of(uids));
        game.state.setCurrentPlayer(uids[0]);
        game.state.setPhase(TurnPhase.BEFORE_ROLL);
        GameSetup.initAssets(game.state, EngineTests.GAME_DATA);
        return game;
    }

    TestGame player(String playerId, Consumer<PlayerState> change) {
        change.accept(state.player(playerId).orElseThrow());
        return this;
    }

    public TestGame at(String playerId, int square) {
        return player(playerId, player -> player.setPosition(square));
    }

    public TestGame cash(String playerId, int cash) {
        return player(playerId, player -> player.setCash(cash));
    }

    public TestGame car(String playerId) {
        return player(playerId, player -> player.setCar(true));
    }

    public TestGame loans(String playerId, int loans) {
        return player(playerId, player -> player.setLoans(loans));
    }

    /** Plays with the given rules; games default to the recommended ones, {@link GameSettings#ORIGINAL} is the printed rules */
    public TestGame settings(GameSettings settings) {
        state.setSettings(settings);
        return this;
    }

    public TestGame loanLimit(LoanLimit loanLimit) {
        state.setSettings(new GameSettings(loanLimit));
        return this;
    }

    public TestGame owns(String playerId, Integer... squares) {
        for (var square : squares) {
            state.property(square).setOwner(playerId);
        }
        return this;
    }

    public TestGame ownsShares(String playerId, String... shares) {
        for (var share : shares) {
            state.share(share).setOwner(playerId);
        }
        return this;
    }

    /** Owns all properties and shares of the group */
    public TestGame ownsGroup(String playerId, String group) {
        owns(playerId, EngineTests.GAME_DATA.group(group).properties().toArray(Integer[]::new));
        EngineTests.GAME_DATA.sharesOf(group).forEach(share -> state.share(share.id()).setOwner(playerId));
        return this;
    }

    public TestGame mortgaged(Integer... squares) {
        for (var square : squares) {
            state.property(square).setMortgaged(true);
        }
        return this;
    }

    public TestGame built(Integer... squares) {
        for (var square : squares) {
            state.property(square).setBuilt(true);
        }
        return this;
    }

    public TestGame boughtThisTurn() {
        state.setBoughtThisTurn(true);
        return this;
    }

    /** In jail with the given turns still to skip */
    public TestGame missedTurns(String playerId, int turns) {
        return player(playerId, player -> { player.setMissedTurns(turns); player.setMissedTurnsInJail(true); });
    }

    /** Left jail and not on square 1 since */
    public TestGame jailExemption(String playerId) {
        return player(playerId, player -> player.setJailExemption(true));
    }

    public TestGame out(String playerId) {
        return player(playerId, player -> player.setOut(true));
    }

    public TestGame turn(String playerId) {
        state.setCurrentPlayer(playerId);
        return this;
    }

    public TestGame afterRoll() {
        state.setPhase(TurnPhase.AFTER_ROLL);
        return this;
    }

    public GameState state() {
        return state;
    }
}
