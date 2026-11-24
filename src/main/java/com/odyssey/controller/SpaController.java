package com.odyssey.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.server.ResponseStatusException;

@Controller
public class SpaController {

    @GetMapping({
        "/search",
        "/checkout",
        "/checkout/**",
        "/trips",
        "/trips/**",
        "/provider",
        "/provider/**",
        "/admin"
    })
    public String forwardSpaRoutes(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri != null && uri.matches(".*\\.(css|js|ico|png|jpg|jpeg|gif|svg|woff|woff2|ttf|eot)$")) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return "forward:/index.html";
    }
}
