package fi.bizhop.finanssi2.game.web;

import fi.bizhop.finanssi2.game.db.Game;
import fi.bizhop.finanssi2.game.db.GameLogEntry;
import fi.bizhop.finanssi2.game.engine.GameCommand;
import fi.bizhop.finanssi2.game.engine.GameSettings;
import fi.bizhop.finanssi2.game.service.GameService;
import fi.bizhop.finanssi2.security.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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
                                 GameCommand command, List<Integer> dice) {}

    @PostMapping("/api/debug/games/{id}/commands")
    public List<GameLogEntry> command(
            @PathVariable String id, @RequestAttribute("user") User user, @RequestBody CommandRequest request) {
        return gameService.debugCommand(id, user, request.actor(), request.expectedVersion(), request.command(), request.dice());
    }

    public record NextCardRequest(String deck, String card, Long expectedVersion) {}

    @PutMapping("/api/debug/games/{id}/next-card")
    public Game nextCard(@PathVariable String id, @RequestAttribute("user") User user,
                         @RequestBody NextCardRequest request) {
        return gameService.nextCard(id, user, request.deck(), request.card(), request.expectedVersion());
    }

    @DeleteMapping("/api/debug/games/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id, @RequestAttribute("user") User user) {
        gameService.deleteDebug(id, user);
    }
}
