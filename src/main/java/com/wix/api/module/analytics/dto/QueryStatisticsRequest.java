package com.wix.api.module.analytics.dto;

import java.util.List;
import java.util.Map;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class QueryStatisticsRequest {

    private DateRange dateRange;
    private List<String> metrics;
    private List<String> dimensions;
    private Map<String, Object> filter;

    @Data
    @Builder
    public static class DateRange {
        private String from;
        private String to;
    }
}
