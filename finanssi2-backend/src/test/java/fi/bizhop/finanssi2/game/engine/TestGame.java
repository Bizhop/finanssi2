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

    TestGame player(String uid, Consumer<PlayerState> change) {
        change.accept(state.player(uid).orElseThrow());
        return this;
    }

    public TestGame at(String uid, int square) {
        return player(uid, player -> player.setPosition(square));
    }

    public TestGame cash(String uid, int cash) {
        return player(uid, player -> player.setCash(cash));
    }

    public TestGame car(String uid) {
        return player(uid, player -> player.setCar(true));
    }

    public TestGame loans(String uid, int loans) {
        return player(uid, player -> player.setLoans(loans));
    }

    public TestGame loanLimit(LoanLimit loanLimit) {
        state.setSettings(new GameSettings(loanLimit));
        return this;
    }

    public TestGame owns(String uid, Integer... squares) {
        for (var square : squares) {
            state.property(square).setOwner(uid);
        }
        return this;
    }

    public TestGame ownsShares(String uid, String... shares) {
        for (var share : shares) {
            state.share(share).setOwner(uid);
        }
        return this;
    }

    /** Owns all properties and shares of the group */
    public TestGame ownsGroup(String uid, String group) {
        owns(uid, EngineTests.GAME_DATA.group(group).properties().toArray(Integer[]::new));
        EngineTests.GAME_DATA.sharesOf(group).forEach(share -> state.share(share.id()).setOwner(uid));
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
    public TestGame missedTurns(String uid, int turns) {
        return player(uid, player -> player.setMissedTurns(turns));
    }

    /** Left jail and not on square 1 since */
    public TestGame jailExemption(String uid) {
        return player(uid, player -> player.setJailExemption(true));
    }

    public TestGame out(String uid) {
        return player(uid, player -> player.setOut(true));
    }

    public TestGame turn(String uid) {
        state.setCurrentPlayer(uid);
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
