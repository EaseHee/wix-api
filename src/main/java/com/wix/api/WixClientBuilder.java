package com.wix.api;

import com.wix.api.auth.WixCredentials;
import com.wix.api.config.WixProperties;
import com.wix.api.http.RetryHandler;
import com.wix.api.http.WixRestClientFactory;
import org.springframework.web.client.RestClient;

public class WixClientBuilder {

    private String apiKey;
    private String accountId;
    private String siteId;
    private String baseUrl;
    private int connectTimeoutSeconds = 10;
    private int readTimeoutSeconds = 30;
    private int maxRetries = 3;
    private long initialRetryDelayMs = 1000;
    private double retryMultiplier = 2.0;
    private long maxRetryDelayMs = 60000;

    public WixClientBuilder apiKey(String apiKey) {
        this.apiKey = apiKey;
        return this;
    }

    public WixClientBuilder accountId(String accountId) {
        this.accountId = accountId;
        return this;
    }

    public WixClientBuilder siteId(String siteId) {
        this.siteId = siteId;
        return this;
    }

    public WixClientBuilder baseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
        return this;
    }

    public WixClientBuilder connectTimeoutSeconds(int connectTimeoutSeconds) {
        this.connectTimeoutSeconds = connectTimeoutSeconds;
        return this;
    }

    public WixClientBuilder readTimeoutSeconds(int readTimeoutSeconds) {
        this.readTimeoutSeconds = readTimeoutSeconds;
        return this;
    }

    public WixClientBuilder maxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
        return this;
    }

    public WixClientBuilder initialRetryDelayMs(long initialRetryDelayMs) {
        this.initialRetryDelayMs = initialRetryDelayMs;
        return this;
    }

    public WixClientBuilder retryMultiplier(double retryMultiplier) {
        this.retryMultiplier = retryMultiplier;
        return this;
    }

    public WixClientBuilder maxRetryDelayMs(long maxRetryDelayMs) {
        this.maxRetryDelayMs = maxRetryDelayMs;
        return this;
    }

    public WixClient build() {
        WixCredentials credentials = WixCredentials.builder()
                .apiKey(apiKey)
                .accountId(accountId)
                .siteId(siteId)
                .build();
        credentials.validate();

        WixProperties properties = new WixProperties();
        if (baseUrl != null) {
            properties.setBaseUrl(baseUrl);
        }
        properties.setConnectTimeoutSeconds(connectTimeoutSeconds);
        properties.setReadTimeoutSeconds(readTimeoutSeconds);
        properties.setMaxRetries(maxRetries);
        properties.setInitialRetryDelayMs(initialRetryDelayMs);
        properties.setRetryMultiplier(retryMultiplier);
        properties.setMaxRetryDelayMs(maxRetryDelayMs);

        RestClient restClient = WixRestClientFactory.create(credentials, properties);
        RetryHandler retryHandler = new RetryHandler(maxRetries, initialRetryDelayMs, retryMultiplier, maxRetryDelayMs);
        return new WixClient(restClient, retryHandler);
    }
}
