package com.wix.api.module;

import java.util.Collections;
import java.util.List;
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

    protected <T> List<T> extractListFromMap(Map<?, ?> response, String key, Class<T> elementType) {
        if (response == null || !response.containsKey(key)) {
            return Collections.emptyList();
        }
        Object value = response.get(key);
        if (value instanceof List<?> list) {
            return list.stream()
                    .map(item -> OBJECT_MAPPER.convertValue(item, elementType))
                    .toList();
        }
        return Collections.emptyList();
    }
}
