package fi.bizhop.finanssi2.game.web;

import fi.bizhop.finanssi2.game.service.DevDiceSource;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/** Dev profile only: helpers for manual testing */
@RestController
@Profile("dev")
@RequiredArgsConstructor
public class DevGameController {
    final DevDiceSource devDiceSource;

    /** Queues die values (1–6) for the game's next rolls, e.g. {@code [6, 6]} */
    @RequestMapping(value = "/api/games/{id}/dev/dice", method = RequestMethod.POST, consumes = "application/json")
    void queueDice(@PathVariable String id, @RequestBody List<Integer> values) {
        if (values.isEmpty() || values.stream().anyMatch(value -> value == null || value < 1 || value > 6)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "values must be 1–6");
        }
        devDiceSource.queue(id, values);
    }
}
