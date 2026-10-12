package fi.bizhop.finanssi2.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/** The application's own settings, all under {@code finanssi2.*} */
@ConfigurationProperties("finanssi2")
public record Finanssi2Properties(Endpoints endpoints, Web web, Debug debug, Firebase firebase) {
    /** Paths that need no sign-in */
    public record Endpoints(List<String> whitelist) {
        public Endpoints {
            whitelist = whitelist == null ? List.of() : List.copyOf(whitelist);
        }
    }

    /** The one browser origin allowed to call the API and open the websocket */
    public record Web(String allowedOrigin) {}

    /** Verified account emails that may use debug games; empty disables debug mode */
    public record Debug(Set<String> allowedEmails) {
        public Debug {
            allowedEmails = allowedEmails == null ? Set.of() : Set.copyOf(allowedEmails);
        }
    }

    public record Firebase(Path credentials) {}

    public Finanssi2Properties {
        endpoints = endpoints == null ? new Endpoints(null) : endpoints;
        debug = debug == null ? new Debug(null) : debug;
    }
}
