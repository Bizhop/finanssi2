package fi.bizhop.finanssi2.game.engine;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/** A player action, sent as JSON with its simple class name as {@code type}, e.g. {@code {"type": "Roll"}} */
@JsonTypeInfo(use = JsonTypeInfo.Id.SIMPLE_NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(GameCommand.Roll.class),
        @JsonSubTypes.Type(GameCommand.EndTurn.class),
        @JsonSubTypes.Type(GameCommand.BuyCar.class),
        @JsonSubTypes.Type(GameCommand.SellCar.class),
})
public sealed interface GameCommand {
    record Roll() implements GameCommand {}

    record EndTurn() implements GameCommand {}

    record BuyCar() implements GameCommand {}

    record SellCar() implements GameCommand {}
}
