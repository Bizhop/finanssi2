package fi.bizhop.finanssi2;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

// Users sign in with Firebase tokens; no in-memory user (and its generated password) is wanted
@ConfigurationPropertiesScan
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class Finanssi2Application {

	public static void main(String[] args) {
		SpringApplication.run(Finanssi2Application.class, args);
	}

}
