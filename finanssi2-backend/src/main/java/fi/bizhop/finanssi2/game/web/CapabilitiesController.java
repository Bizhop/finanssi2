package fi.bizhop.finanssi2.game.web;

import fi.bizhop.finanssi2.game.service.DebugAccess;
import fi.bizhop.finanssi2.security.User;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class CapabilitiesController {
    final DebugAccess debugAccess;
    public record Capabilities(boolean debugMode) {}

    @GetMapping("/api/me/capabilities")
    public Capabilities capabilities(@RequestAttribute("user") User user) {
        return new Capabilities(debugAccess.allowed(user));
    }
}
