package fi.bizhop.finanssi2.game.engine;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import fi.bizhop.finanssi2.game.data.SquareType;

import java.util.List;

/** Something that happened in a game, in the order it happened. Serialized with its simple class name as {@code type}. */
@JsonTypeInfo(use = JsonTypeInfo.Id.SIMPLE_NAME, property = "type")
public sealed interface GameEvent {
    record PlayerJoined(String player, String name, int piece) implements GameEvent {}

    record PlayerLeft(String player) implements GameEvent {}

    /** A player's roll for the starting order; tied highest rollers roll again in the next round */
    record StartingRoll(String player, int round, List<Integer> dice) implements GameEvent {}

    record GameStarted(List<String> turnOrder, int startingCash) implements GameEvent {}

    record TurnStarted(String player) implements GameEvent {}

    record DiceRolled(String player, List<Integer> dice) implements GameEvent {}

    record PieceMoved(String player, int from, int to) implements GameEvent {}

    record LandedOn(String player, int square) implements GameEvent {}

    /** Landed on a square whose effect is not implemented yet */
    record NotImplemented(String player, int square, SquareType squareType) implements GameEvent {}

    record CarBought(String player, int price) implements GameEvent {}

    record CarSold(String player, int price) implements GameEvent {}

    record TurnEnded(String player) implements GameEvent {}
}
