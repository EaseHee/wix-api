package com.wix.api.http;

import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RetryHandler {

    private static final Logger log = LoggerFactory.getLogger(RetryHandler.class);

    private final int maxRetries;
    private final long initialDelayMs;
    private final double multiplier;
    private final long maxDelayMs;

    public RetryHandler(int maxRetries, long initialDelayMs, double multiplier, long maxDelayMs) {
        this.maxRetries = maxRetries;
        this.initialDelayMs = initialDelayMs;
        this.multiplier = multiplier;
        this.maxDelayMs = maxDelayMs;
    }

    public <T> T executeWithRetry(Supplier<T> operation) {
        int attempt = 0;
        while (true) {
            try {
                return operation.get();
            } catch (WixApiException e) {
                if (!isRetryable(e)) {
                    throw e;
                }
                attempt++;
                if (attempt >= maxRetries) {
                    throw e;
                }
                long delay = Math.min((long) (initialDelayMs * Math.pow(multiplier, attempt)), maxDelayMs);
                log.warn("Wix API error {} (attempt {}/{}), retrying in {}ms: {}",
                        e.getStatusCode(), attempt, maxRetries, delay, e.getMessage());
                sleep(delay);
            }
        }
    }

    public void executeWithRetryNoReturn(Runnable operation) {
        executeWithRetry(() -> {
            operation.run();
            return null;
        });
    }

    private boolean isRetryable(WixApiException e) {
        return e instanceof WixRateLimitException || e.getStatusCode() >= 500;
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Retry interrupted", e);
        }
    }
}
