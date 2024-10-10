package fi.bizhop.finanssi2.web;

import fi.bizhop.finanssi2.web.security.User;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {
    @RequestMapping(value = "/api/hello", method = RequestMethod.GET, produces = "text/plain")
    @ResponseBody String hello() {
        return "Hello, world!";
    }

    @RequestMapping(value = "/api/secure-hello", method = RequestMethod.GET, produces = "application/json")
    @ResponseBody User secureHello(@RequestAttribute("user") User user) {
        return user;
    }

    @RequestMapping(value = "/api/secure-hello", method = RequestMethod.POST, produces = "application/json")
    @ResponseBody User secureHelloPost(@RequestAttribute("user") User user) {
        return user;
    }
}
