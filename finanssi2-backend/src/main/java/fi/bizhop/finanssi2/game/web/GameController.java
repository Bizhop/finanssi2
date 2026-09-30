package fi.bizhop.finanssi2.game.web;

import fi.bizhop.finanssi2.game.db.Game;
import fi.bizhop.finanssi2.game.db.GameLogEntry;
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

    @RequestMapping(value = "/api/games/{id}", method = RequestMethod.GET, produces = "application/json")
    @ResponseBody Game get(@PathVariable String id) {
        return gameService.get(id);
    }

    /** The game's events after sequence number {@code after}, oldest first; the whole log by default */
    @RequestMapping(value = "/api/games/{id}/events", method = RequestMethod.GET, produces = "application/json")
    @ResponseBody List<GameLogEntry> events(@PathVariable String id, @RequestParam(defaultValue = "0") int after) {
        if (after < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "after must not be negative");
        }
        return gameService.events(id, after);
    }

    @RequestMapping(value = "/api/games/{id}/join", method = RequestMethod.POST, produces = "application/json")
    @ResponseBody Game join(@PathVariable String id, @RequestAttribute("user") User user) {
        return gameService.join(id, user);
    }

    @RequestMapping(value = "/api/games/{id}/leave", method = RequestMethod.POST)
    void leave(@PathVariable String id, @RequestAttribute("user") User user) {
        gameService.leave(id, user);
    }

    @RequestMapping(value = "/api/games/{id}/start", method = RequestMethod.POST, produces = "application/json")
    @ResponseBody Game start(@PathVariable String id, @RequestAttribute("user") User user) {
        return gameService.start(id, user);
    }

    @ExceptionHandler(GameNotFoundException.class)
    ProblemDetail notFound(GameNotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(NotAllowedException.class)
    ProblemDetail notAllowed(NotAllowedException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, e.getMessage());
    }

    @ExceptionHandler(RuleViolation.class)
    ProblemDetail ruleViolation(RuleViolation e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail concurrentChange() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "The game was changed at the same time; reload and try again");
    }
}
