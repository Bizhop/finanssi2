package fi.bizhop.finanssi2.game.web;

import fi.bizhop.finanssi2.game.db.Game;
import fi.bizhop.finanssi2.game.db.GameLogEntry;
import fi.bizhop.finanssi2.game.engine.GameCommand;
import fi.bizhop.finanssi2.game.engine.GameSettings;
import fi.bizhop.finanssi2.game.service.GameService;
import fi.bizhop.finanssi2.security.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class GameController {
    final GameService gameService;

    @PostMapping(value = "/api/games", produces = "application/json")
    Game create(@RequestAttribute("user") User user) {
        return gameService.create(user);
    }

    /** Games in the lobby and games the user is in, newest first */
    @GetMapping(value = "/api/games", produces = "application/json")
    List<Game> list(@RequestAttribute("user") User user) {
        return gameService.list(user);
    }

    /** The game, with the commands the user may send right now */
    @GetMapping(value = "/api/games/{id}", produces = "application/json")
    GameView get(@PathVariable String id, @RequestAttribute("user") User user) {
        var game = gameService.get(id, user);
        return new GameView(game, gameService.allowedCommands(game, user), gameService.actingPlayer(game, user), gameService.options(game, user));
    }

    /** Runs an in-game command, e.g. {@code {"type": "Roll"}}, and returns the events it caused */
    @PostMapping(value = "/api/games/{id}/commands", consumes = "application/json", produces = "application/json")
    List<GameLogEntry> command(@PathVariable String id, @RequestBody GameCommand command,
                                             @RequestAttribute("user") User user) {
        return gameService.command(id, user, command);
    }

    /** The game's events after sequence number {@code after}, oldest first; the whole log by default */
    @GetMapping(value = "/api/games/{id}/events", produces = "application/json")
    List<GameLogEntry> events(@PathVariable String id, @RequestParam(defaultValue = "0") int after, @RequestAttribute("user") User user) {
        if (after < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "after must not be negative");
        }
        return gameService.events(id, after, user);
    }

    @PostMapping(value = "/api/games/{id}/join", produces = "application/json")
    Game join(@PathVariable String id, @RequestAttribute("user") User user) {
        return gameService.join(id, user);
    }

    @PostMapping("/api/games/{id}/leave")
    void leave(@PathVariable String id, @RequestAttribute("user") User user) {
        gameService.leave(id, user);
    }

    /** Sets the house rules, e.g. {@code {"loanLimit": "UNLIMITED"}}; creator only, in the lobby */
    @PutMapping(value = "/api/games/{id}/settings", consumes = "application/json", produces = "application/json")
    Game changeSettings(@PathVariable String id, @RequestBody GameSettings settings,
                                      @RequestAttribute("user") User user) {
        if (settings.loanLimit() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "loanLimit is required");
        }
        return gameService.changeSettings(id, user, settings);
    }

    @PostMapping(value = "/api/games/{id}/start", produces = "application/json")
    Game start(@PathVariable String id, @RequestAttribute("user") User user) {
        return gameService.start(id, user);
    }

}
