package fi.bizhop.finanssi2.game.web;

import fi.bizhop.finanssi2.game.db.Game;
import fi.bizhop.finanssi2.game.engine.GameSettings;
import fi.bizhop.finanssi2.game.service.GameService;
import fi.bizhop.finanssi2.security.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class DebugGameController {
    final GameService gameService;

    public record CreateRequest(Integer playerCount, GameSettings settings) {}

    @PostMapping("/api/debug/games")
    public Game create(@RequestAttribute("user") User user, @RequestBody CreateRequest request) {
        return gameService.createDebug(user, request.playerCount() == null ? 2 : request.playerCount(),
                request.settings() == null ? GameSettings.DEFAULT : request.settings());
    }

    public record CommandRequest(String actor, Long expectedVersion,
                                 fi.bizhop.finanssi2.game.engine.GameCommand command, java.util.List<Integer> dice) {}

    @PostMapping("/api/debug/games/{id}/commands")
    public java.util.List<fi.bizhop.finanssi2.game.db.GameLogEntry> command(
            @PathVariable String id, @RequestAttribute("user") User user, @RequestBody CommandRequest request) {
        return gameService.debugCommand(id, user, request.actor(), request.expectedVersion(), request.command(), request.dice());
    }

    @DeleteMapping("/api/debug/games/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id, @RequestAttribute("user") User user) {
        gameService.deleteDebug(id, user);
    }
}
