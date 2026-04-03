package com.wix.api.auth;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;

class WixAuthInterceptorTest {

    @Test
    void shouldAddApiKeyAndSiteIdHeaders() throws IOException {
        WixCredentials credentials = WixCredentials.builder()
                .apiKey("my-api-key")
                .siteId("my-site-id")
                .build();

        WixAuthInterceptor interceptor = new WixAuthInterceptor(credentials);

        HttpHeaders capturedHeaders = new HttpHeaders();
        HttpRequest mockRequest = createMockRequest(capturedHeaders);
        ClientHttpRequestExecution mockExecution = (req, body) -> createMockResponse();

        interceptor.intercept(mockRequest, new byte[0], mockExecution);

        assertEquals("my-api-key", capturedHeaders.getFirst("Authorization"));
        assertEquals("my-site-id", capturedHeaders.getFirst("wix-site-id"));
        assertNull(capturedHeaders.getFirst("wix-account-id"));
    }

    @Test
    void shouldAddApiKeyAndAccountIdHeaders() throws IOException {
        WixCredentials credentials = WixCredentials.builder()
                .apiKey("my-api-key")
                .accountId("my-account-id")
                .build();

        WixAuthInterceptor interceptor = new WixAuthInterceptor(credentials);

        HttpHeaders capturedHeaders = new HttpHeaders();
        HttpRequest mockRequest = createMockRequest(capturedHeaders);
        ClientHttpRequestExecution mockExecution = (req, body) -> createMockResponse();

        interceptor.intercept(mockRequest, new byte[0], mockExecution);

        assertEquals("my-api-key", capturedHeaders.getFirst("Authorization"));
        assertEquals("my-account-id", capturedHeaders.getFirst("wix-account-id"));
        assertNull(capturedHeaders.getFirst("wix-site-id"));
    }

    private HttpRequest createMockRequest(HttpHeaders headers) {
        return new HttpRequest() {
            @Override
            public HttpMethod getMethod() { return HttpMethod.GET; }
            @Override
            public URI getURI() { return URI.create("https://www.wixapis.com/test"); }
            @Override
            public HttpHeaders getHeaders() { return headers; }
        };
    }

    private ClientHttpResponse createMockResponse() {
        return new ClientHttpResponse() {
            @Override
            public HttpStatusCode getStatusCode() { return HttpStatusCode.valueOf(200); }
            @Override
            public String getStatusText() { return "OK"; }
            @Override
            public void close() {}
            @Override
            public java.io.InputStream getBody() { return new ByteArrayInputStream(new byte[0]); }
            @Override
            public HttpHeaders getHeaders() { return new HttpHeaders(); }
        };
    }
}
