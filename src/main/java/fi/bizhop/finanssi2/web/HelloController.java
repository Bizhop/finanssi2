package fi.bizhop.finanssi2.web;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {
    @RequestMapping(value = "/hello", method = RequestMethod.GET, produces = "text/plain")
    @ResponseBody String hello() {
        return "Hello, world!";
    }
}
