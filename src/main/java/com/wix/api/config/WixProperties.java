package com.wix.api.config;

import lombok.Data;

@Data
public class WixProperties {

    private String apiKey;
    private String accountId;
    private String siteId;
    private String baseUrl = "https://www.wixapis.com";
    private int connectTimeoutSeconds = 10;
    private int readTimeoutSeconds = 30;
    private int maxRetries = 3;
    private long initialRetryDelayMs = 1000;
    private double retryMultiplier = 2.0;
    private long maxRetryDelayMs = 60000;
}
