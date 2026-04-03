package com.wix.api.auth;

import java.io.IOException;

import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

public class WixAuthInterceptor implements ClientHttpRequestInterceptor {

    private final WixCredentials credentials;

    public WixAuthInterceptor(WixCredentials credentials) {
        this.credentials = credentials;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body,
            ClientHttpRequestExecution execution) throws IOException {
        request.getHeaders().set("Authorization", credentials.getApiKey());
        if (credentials.getAccountId() != null && !credentials.getAccountId().isBlank()) {
            request.getHeaders().set("wix-account-id", credentials.getAccountId());
        }
        if (credentials.getSiteId() != null && !credentials.getSiteId().isBlank()) {
            request.getHeaders().set("wix-site-id", credentials.getSiteId());
        }
        return execution.execute(request, body);
    }
}
