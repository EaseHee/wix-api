package com.wix.api.http;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class RetryHandlerTest {

    private final RetryHandler retryHandler = new RetryHandler(3, 10, 2.0, 100);

    @Test
    void shouldReturnImmediatelyOnSuccess() {
        String result = retryHandler.executeWithRetry(() -> "success");
        assertEquals("success", result);
    }

    @Test
    void shouldRetryOnRateLimitThenSucceed() {
        AtomicInteger attempts = new AtomicInteger(0);

        String result = retryHandler.executeWithRetry(() -> {
            if (attempts.incrementAndGet() < 3) {
                throw new WixRateLimitException("rate limited", null);
            }
            return "success";
        });

        assertEquals("success", result);
        assertEquals(3, attempts.get());
    }

    @Test
    void shouldRetryOnServerErrorThenSucceed() {
        AtomicInteger attempts = new AtomicInteger(0);

        String result = retryHandler.executeWithRetry(() -> {
            if (attempts.incrementAndGet() < 2) {
                throw new WixApiException(500, "server error", null);
            }
            return "success";
        });

        assertEquals("success", result);
        assertEquals(2, attempts.get());
    }

    @Test
    void shouldNotRetryOnClientError() {
        AtomicInteger attempts = new AtomicInteger(0);

        assertThrows(WixApiException.class, () ->
                retryHandler.executeWithRetry(() -> {
                    attempts.incrementAndGet();
                    throw new WixApiException(400, "bad request", null);
                })
        );

        assertEquals(1, attempts.get());
    }

    @Test
    void shouldThrowAfterMaxRetries() {
        AtomicInteger attempts = new AtomicInteger(0);

        assertThrows(WixRateLimitException.class, () ->
                retryHandler.executeWithRetry(() -> {
                    attempts.incrementAndGet();
                    throw new WixRateLimitException("rate limited", null);
                })
        );

        assertEquals(3, attempts.get());
    }

    @Test
    void shouldExecuteRunnableWithRetry() {
        AtomicInteger attempts = new AtomicInteger(0);

        retryHandler.executeWithRetryNoReturn(() -> {
            if (attempts.incrementAndGet() < 2) {
                throw new WixRateLimitException("rate limited", null);
            }
        });

        assertEquals(2, attempts.get());
    }
}
