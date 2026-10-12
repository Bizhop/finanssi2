package fi.bizhop.finanssi2.game.service;

import fi.bizhop.finanssi2.game.db.Game;
import org.jspecify.annotations.Nullable;

/** Broadcast on the lobby topic when a game is created, joined, left or started; {@code game} is null when it was deleted */
public record LobbyChange(String gameId, @Nullable Game game) {}
