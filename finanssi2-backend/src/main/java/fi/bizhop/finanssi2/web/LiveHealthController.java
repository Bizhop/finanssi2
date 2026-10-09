package fi.bizhop.finanssi2.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LiveHealthController {
    @GetMapping(value = "/api/health/live", produces = "text/plain")
    public String live() {
        return "ok";
    }
}
