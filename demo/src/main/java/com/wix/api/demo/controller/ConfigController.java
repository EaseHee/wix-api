package com.wix.api.demo.controller;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ConfigController {

    @Value("${wix.defaults.api-key:}")
    private String defaultApiKey;

    @Value("${wix.defaults.site-id:}")
    private String defaultSiteId;

    @GetMapping("/api/config/defaults")
    public Map<String, String> getDefaults() {
        return Map.of(
                "apiKey", defaultApiKey != null ? defaultApiKey : "",
                "siteId", defaultSiteId != null ? defaultSiteId : ""
        );
    }
}
