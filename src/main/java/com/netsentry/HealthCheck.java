package com.netsentry;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthCheck {
    @GetMapping("/")
    public String home () {
        String message = "Welcome to the / page";
        return message;
    }

    @GetMapping("/health")
    public String health() {
        return "NetSentry Health Check";
    }
    @GetMapping("/new")
    public String new_func () {
        return "hha";
    }
}

