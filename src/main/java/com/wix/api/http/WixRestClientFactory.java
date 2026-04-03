package com.wix.api.http;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.wix.api.auth.WixAuthInterceptor;
import com.wix.api.auth.WixCredentials;
import com.wix.api.config.WixProperties;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.DefaultUriBuilderFactory;

public class WixRestClientFactory {

    private static final String DEFAULT_BASE_URL = "https://www.wixapis.com";
    static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public static RestClient create(WixCredentials credentials, WixProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(properties.getConnectTimeoutSeconds()));
        requestFactory.setReadTimeout(Duration.ofSeconds(properties.getReadTimeoutSeconds()));

        String baseUrl = properties.getBaseUrl() != null ? properties.getBaseUrl() : DEFAULT_BASE_URL;

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .requestInterceptor(new WixAuthInterceptor(credentials))
                .messageConverters(converters -> {
                    converters.removeIf(c -> c instanceof MappingJackson2HttpMessageConverter);
                    converters.add(new MappingJackson2HttpMessageConverter(OBJECT_MAPPER));
                })
                .defaultStatusHandler(statusCode -> statusCode.isError(), (request, response) -> {
                    handleErrorResponse(response);
                })
                .build();
    }

    private static void handleErrorResponse(ClientHttpResponse response) throws java.io.IOException {
        int statusCode = response.getStatusCode().value();
        String body = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);

        WixErrorResponse errorResponse = null;
        try {
            errorResponse = OBJECT_MAPPER.readValue(body, WixErrorResponse.class);
        } catch (Exception ignored) {
        }

        if (statusCode == 429) {
            throw new WixRateLimitException(body, errorResponse);
        }
        throw new WixApiException(statusCode, body, errorResponse);
    }
}
