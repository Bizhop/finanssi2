package fi.bizhop.finanssi2.game.web;

import fi.bizhop.finanssi2.game.db.Game;
import fi.bizhop.finanssi2.game.db.GameLogEntry;
import fi.bizhop.finanssi2.game.engine.GameCommand;
import fi.bizhop.finanssi2.game.engine.GameSettings;
import fi.bizhop.finanssi2.game.engine.NotYourTurn;
import fi.bizhop.finanssi2.game.engine.RuleViolation;
import fi.bizhop.finanssi2.game.service.GameNotFoundException;
import fi.bizhop.finanssi2.game.service.GameService;
import fi.bizhop.finanssi2.game.service.NotAllowedException;
import fi.bizhop.finanssi2.security.User;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class GameController {
    final GameService gameService;

    @RequestMapping(value = "/api/games", method = RequestMethod.POST, produces = "application/json")
    @ResponseBody Game create(@RequestAttribute("user") User user) {
        return gameService.create(user);
    }

    /** Games in the lobby and games the user is in, newest first */
    @RequestMapping(value = "/api/games", method = RequestMethod.GET, produces = "application/json")
    @ResponseBody List<Game> list(@RequestAttribute("user") User user) {
        return gameService.list(user);
    }

    /** The game, with the commands the user may send right now */
    @RequestMapping(value = "/api/games/{id}", method = RequestMethod.GET, produces = "application/json")
    @ResponseBody GameView get(@PathVariable String id, @RequestAttribute("user") User user) {
        var game = gameService.get(id, user);
        return new GameView(game, gameService.allowedCommands(game, user));
    }

    /** Runs an in-game command, e.g. {@code {"type": "Roll"}}, and returns the events it caused */
    @RequestMapping(value = "/api/games/{id}/commands", method = RequestMethod.POST, consumes = "application/json",
            produces = "application/json")
    @ResponseBody List<GameLogEntry> command(@PathVariable String id, @RequestBody GameCommand command,
                                             @RequestAttribute("user") User user) {
        return gameService.command(id, user, command);
    }

    /** The game's events after sequence number {@code after}, oldest first; the whole log by default */
    @RequestMapping(value = "/api/games/{id}/events", method = RequestMethod.GET, produces = "application/json")
    @ResponseBody List<GameLogEntry> events(@PathVariable String id, @RequestParam(defaultValue = "0") int after, @RequestAttribute("user") User user) {
        if (after < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "after must not be negative");
        }
        return gameService.events(id, after, user);
    }

    @RequestMapping(value = "/api/games/{id}/join", method = RequestMethod.POST, produces = "application/json")
    @ResponseBody Game join(@PathVariable String id, @RequestAttribute("user") User user) {
        return gameService.join(id, user);
    }

    @RequestMapping(value = "/api/games/{id}/leave", method = RequestMethod.POST)
    void leave(@PathVariable String id, @RequestAttribute("user") User user) {
        gameService.leave(id, user);
    }

    /** Sets the house rules, e.g. {@code {"loanLimit": "UNLIMITED"}}; creator only, in the lobby */
    @RequestMapping(value = "/api/games/{id}/settings", method = RequestMethod.PUT, consumes = "application/json",
            produces = "application/json")
    @ResponseBody Game changeSettings(@PathVariable String id, @RequestBody GameSettings settings,
                                      @RequestAttribute("user") User user) {
        if (settings.loanLimit() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "loanLimit is required");
        }
        return gameService.changeSettings(id, user, settings);
    }

    @RequestMapping(value = "/api/games/{id}/start", method = RequestMethod.POST, produces = "application/json")
    @ResponseBody Game start(@PathVariable String id, @RequestAttribute("user") User user) {
        return gameService.start(id, user);
    }

}
