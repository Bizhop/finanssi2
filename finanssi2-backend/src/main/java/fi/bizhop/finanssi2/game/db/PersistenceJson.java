package fi.bizhop.finanssi2.game.db;

import fi.bizhop.finanssi2.game.engine.GameEvent;
import tools.jackson.databind.jsontype.NamedType;
import tools.jackson.databind.json.JsonMapper;

import java.util.Arrays;

/**
 * The mappers behind the JSONB columns. Deliberately not Spring beans: the application's own mapper is the one that renders API
 * responses, and these also write what the API must never carry (deck order, sealed bids).
 */
final class PersistenceJson {
    private PersistenceJson() {}

    /** Game state, as {@link fi.bizhop.finanssi2.game.engine.GameState}'s own annotations describe it */
    static final JsonMapper STATE = JsonMapper.builder().build();

    /** Game events, tagged with their simple class name */
    static final JsonMapper EVENTS = JsonMapper.builder()
            .registerSubtypes(Arrays.stream(GameEvent.class.getPermittedSubclasses())
                    .map(type -> new NamedType(type, type.getSimpleName())).toArray(NamedType[]::new))
            .build();
}
