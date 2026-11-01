package com.codestep.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * The React app uses real paths (BrowserRouter), so a reload or a shared link to a problem must
 * get index.html. In docker-compose nginx does this; in single-container mode the API serves the app.
 */
@Controller
public class SpaController {

    @GetMapping("/problems/{slug}")
    String problem() {
        return "forward:/index.html";
    }
}
