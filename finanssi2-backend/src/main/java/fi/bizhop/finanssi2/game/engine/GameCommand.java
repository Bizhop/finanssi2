package fi.bizhop.finanssi2.game.engine;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.List;

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
        @JsonSubTypes.Type(GameCommand.BuyProperty.class),
        @JsonSubTypes.Type(GameCommand.BuyShare.class),
        @JsonSubTypes.Type(GameCommand.Mortgage.class),
        @JsonSubTypes.Type(GameCommand.Redeem.class),
        @JsonSubTypes.Type(GameCommand.SellBackProperty.class),
        @JsonSubTypes.Type(GameCommand.SellBackShare.class),
        @JsonSubTypes.Type(GameCommand.Build.class),
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

    record BuyProperty(int square) implements GameCommand {}

    record BuyShare(String share) implements GameCommand {}

    record Mortgage(int square) implements GameCommand {}

    /** Pays off a mortgage */
    record Redeem(int square) implements GameCommand {}

    record SellBackProperty(int square) implements GameCommand {}

    record SellBackShare(String share) implements GameCommand {}

    /** Builds on each of the listed properties; all or nothing */
    record Build(List<Integer> squares) implements GameCommand {
        public Build {
            squares = squares == null ? List.of() : List.copyOf(squares);
        }
    }
}
