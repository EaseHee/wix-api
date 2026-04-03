package com.wix.api.demo;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.wix.api.WixClient;
import org.springframework.stereotype.Component;

@Component
public class WixClientProvider {

    private final Map<String, WixClient> cache = new ConcurrentHashMap<>();

    public WixClient get(String apiKey, String siteId) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("API Key 필수. 상단에서 입력 후 재시도.");
        }
        String key = apiKey + "|" + (siteId != null ? siteId : "");
        return cache.computeIfAbsent(key, k ->
                WixClient.builder()
                        .apiKey(apiKey)
                        .siteId(siteId != null && !siteId.isBlank() ? siteId : null)
                        .accountId(siteId == null || siteId.isBlank() ? "default" : null)
                        .build()
        );
    }
}
