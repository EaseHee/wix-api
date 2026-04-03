package com.wix.api.auth;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class WixCredentials {

    private final String apiKey;
    private final String accountId;
    private final String siteId;

    public void validate() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("API key is required");
        }
        if ((accountId == null || accountId.isBlank()) && (siteId == null || siteId.isBlank())) {
            throw new IllegalArgumentException("Either accountId or siteId is required");
        }
    }
}
