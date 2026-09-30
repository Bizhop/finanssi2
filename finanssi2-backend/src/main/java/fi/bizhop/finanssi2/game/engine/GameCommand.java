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
        @JsonSubTypes.Type(GameCommand.TakeLoan.class),
        @JsonSubTypes.Type(GameCommand.RepayLoan.class),
        @JsonSubTypes.Type(GameCommand.Pay.class),
        @JsonSubTypes.Type(GameCommand.DeclareBankruptcy.class),
})
public sealed interface GameCommand {
    record Roll() implements GameCommand {}

    record EndTurn() implements GameCommand {}

    record BuyCar() implements GameCommand {}

    record SellCar() implements GameCommand {}

    record TakeLoan() implements GameCommand {}

    record RepayLoan() implements GameCommand {}

    /** Pays the payment of a pending {@link PendingDecision.RaiseFunds} */
    record Pay() implements GameCommand {}

    /** Only when the payment of a pending {@link PendingDecision.RaiseFunds} cannot be raised in any way */
    record DeclareBankruptcy() implements GameCommand {}
}
