package com.wix.api.module.analytics;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

import com.wix.api.http.RetryHandler;
import com.wix.api.module.AbstractWixService;
import com.wix.api.module.analytics.dto.QueryStatisticsRequest;
import com.wix.api.module.analytics.dto.QueryStatisticsResponse;
import org.springframework.web.client.RestClient;

public class AnalyticsService extends AbstractWixService {

    private static final int MAX_RETENTION_DAYS = 62;

    public AnalyticsService(RestClient restClient, RetryHandler retryHandler) {
        super(restClient, retryHandler);
    }

    public QueryStatisticsResponse queryStatistics(QueryStatisticsRequest request) {
        validateDateRange(request);
        return retryHandler.executeWithRetry(() ->
                restClient.get()
                        .uri(uriBuilder -> {
                            uriBuilder.path("/analytics/v2/site-analytics/data");
                            if (request.getDateRange() != null) {
                                if (request.getDateRange().getFrom() != null)
                                    uriBuilder.queryParam("dateRange.from", request.getDateRange().getFrom());
                                if (request.getDateRange().getTo() != null)
                                    uriBuilder.queryParam("dateRange.to", request.getDateRange().getTo());
                            }
                            if (request.getMetrics() != null) {
                                for (String metric : request.getMetrics()) {
                                    uriBuilder.queryParam("metrics", metric);
                                }
                            }
                            if (request.getDimensions() != null) {
                                for (String dim : request.getDimensions()) {
                                    uriBuilder.queryParam("dimensions", dim);
                                }
                            }
                            return uriBuilder.build();
                        })
                        .retrieve()
                        .body(QueryStatisticsResponse.class)
        );
    }

    private void validateDateRange(QueryStatisticsRequest request) {
        if (request.getDateRange() == null || request.getDateRange().getFrom() == null) {
            return;
        }
        try {
            LocalDate from = LocalDate.parse(request.getDateRange().getFrom(), DateTimeFormatter.ISO_DATE);
            long daysAgo = ChronoUnit.DAYS.between(from, LocalDate.now());
            if (daysAgo > MAX_RETENTION_DAYS) {
                throw new IllegalArgumentException(
                        "Wix Analytics 데이터는 최근 " + MAX_RETENTION_DAYS +
                                "일까지만 조회 가능. 요청 날짜(" + from + ")는 " + daysAgo + "일 전.");
            }
        } catch (java.time.format.DateTimeParseException ignored) {
        }
    }
}
