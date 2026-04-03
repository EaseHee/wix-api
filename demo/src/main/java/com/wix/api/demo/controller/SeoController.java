package com.wix.api.demo.controller;

import com.wix.api.WixClient;
import com.wix.api.demo.WixClientProvider;
import com.wix.api.module.seo.dto.RobotsTxt;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/seo")
@RequiredArgsConstructor
public class SeoController {

    private final WixClientProvider provider;

    @GetMapping("/robots")
    public RobotsTxt getRobotsTxt(
            @RequestHeader(value="X-Wix-Api-Key",required=false) String apiKey,
            @RequestHeader(value="X-Wix-Site-Id",required=false) String siteId) {
        return wix(apiKey, siteId).seo().getRobotsTxt();
    }

    private WixClient wix(String apiKey, String siteId) {
        return provider.get(apiKey, siteId);
    }
}
