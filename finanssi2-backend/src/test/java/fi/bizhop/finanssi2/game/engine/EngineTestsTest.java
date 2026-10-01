package fi.bizhop.finanssi2.game.engine;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collections;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class EngineTestsTest {
    @Test
    void snapshotDetectsDeckAndPlayerOrderChanges() {
        var state = TestGame.players("a", "b").state();
        state.getFinanceNewsDeck().addAll(java.util.List.of("FL-01", "FL-02"));
        var before = EngineTests.Snapshot.of(state);

        Collections.reverse(state.getFinanceNewsDeck());
        assertNotEquals(before, EngineTests.Snapshot.of(state));
        Collections.reverse(state.getFinanceNewsDeck());
        assertEquals(before, EngineTests.Snapshot.of(state));

        Collections.reverse(state.getPlayers());
        assertNotEquals(before, EngineTests.Snapshot.of(state));
    }

    @Test
    void snapshotDetachesMutablePlayersAndAssets() {
        var state = TestGame.players("a", "b").state();
        var before = EngineTests.Snapshot.of(state);
        state.current().setCash(0);
        state.property(3).setBuilt(true);
        state.getShares().getFirst().setOwner("a");
        state.bond(1).setOwner("b");

        assertEquals(75_000, before.players().getFirst().cash());
        assertEquals(false, before.properties().getFirst().built());
        assertEquals(null, before.shares().getFirst().owner());
        assertEquals(null, before.bonds().getFirst().owner());
        assertNotEquals(before, EngineTests.Snapshot.of(state));
    }

    @Test
    void snapshotsCoverEveryStateField() {
        assertFieldsCovered(GameState.class, EngineTests.Snapshot.class);
        assertFieldsCovered(PlayerState.class, EngineTests.PlayerSnapshot.class);
        assertFieldsCovered(PropertyState.class, EngineTests.PropertySnapshot.class);
        assertFieldsCovered(ShareState.class, EngineTests.ShareSnapshot.class);
        assertFieldsCovered(BondState.class, EngineTests.BondSnapshot.class);
    }

    static void assertFieldsCovered(Class<?> state, Class<?> snapshot) {
        var fields = Arrays.stream(state.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()) && !field.isSynthetic())
                .map(field -> field.getName())
                .collect(Collectors.toUnmodifiableSet());
        var components = Arrays.stream(snapshot.getRecordComponents()).map(component -> component.getName())
                .collect(Collectors.toUnmodifiableSet());
        assertEquals(fields, components, state.getSimpleName() + " snapshot must cover every field");
    }
}
