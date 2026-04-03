package com.wix.api.module;

import java.util.Map;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.wix.api.http.RetryHandler;
import org.springframework.web.client.RestClient;

public abstract class AbstractWixService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    protected final RestClient restClient;
    protected final RetryHandler retryHandler;

    protected AbstractWixService(RestClient restClient, RetryHandler retryHandler) {
        this.restClient = restClient;
        this.retryHandler = retryHandler;
    }

    protected <T> T extractFromMap(Map<?, ?> response, String key, Class<T> type) {
        if (response == null || !response.containsKey(key)) {
            return null;
        }
        return OBJECT_MAPPER.convertValue(response.get(key), type);
    }
}
