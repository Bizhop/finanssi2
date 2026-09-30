package fi.bizhop.finanssi2.game.engine;

import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.List;

/** Something that happened in a game, in the order it happened. Serialized with its simple class name as {@code type}. */
@JsonTypeInfo(use = JsonTypeInfo.Id.SIMPLE_NAME, property = "type")
public sealed interface GameEvent {
    record PlayerJoined(String player, String name, int piece) implements GameEvent {}

    record PlayerLeft(String player) implements GameEvent {}

    /** A player's roll for the starting order; tied highest rollers roll again in the next round */
    record StartingRoll(String player, int round, List<Integer> dice) implements GameEvent {}

    record GameStarted(List<String> turnOrder, int startingCash) implements GameEvent {}
}
