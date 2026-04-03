package com.wix.api;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class WixClientBuilderTest {

    @Test
    void shouldBuildClientWithSiteId() {
        WixClient client = WixClient.builder()
                .apiKey("test-api-key")
                .siteId("test-site-id")
                .build();

        assertNotNull(client);
        assertNotNull(client.contacts());
        assertNotNull(client.cms());
        assertNotNull(client.inbox());
        assertNotNull(client.members());
        assertNotNull(client.analytics());
        assertNotNull(client.seo());
    }

    @Test
    void shouldBuildClientWithAccountId() {
        WixClient client = WixClient.builder()
                .apiKey("test-api-key")
                .accountId("test-account-id")
                .build();

        assertNotNull(client);
    }

    @Test
    void shouldFailWithoutApiKey() {
        assertThrows(IllegalArgumentException.class, () ->
                WixClient.builder()
                        .siteId("test-site-id")
                        .build()
        );
    }

    @Test
    void shouldFailWithoutSiteIdOrAccountId() {
        assertThrows(IllegalArgumentException.class, () ->
                WixClient.builder()
                        .apiKey("test-api-key")
                        .build()
        );
    }

    @Test
    void shouldAcceptCustomConfiguration() {
        WixClient client = WixClient.builder()
                .apiKey("test-api-key")
                .siteId("test-site-id")
                .baseUrl("https://custom.wixapis.com")
                .connectTimeoutSeconds(5)
                .readTimeoutSeconds(15)
                .maxRetries(5)
                .build();

        assertNotNull(client);
    }
}
