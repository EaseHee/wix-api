package com.wix.api.demo.controller;

import java.util.List;

import com.wix.api.WixClient;
import com.wix.api.demo.WixClientProvider;
import com.wix.api.module.analytics.dto.QueryStatisticsRequest;
import com.wix.api.module.analytics.dto.QueryStatisticsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final WixClientProvider provider;

    @GetMapping("/data")
    public QueryStatisticsResponse getData(
            @RequestHeader(value="X-Wix-Api-Key",required=false) String apiKey,
            @RequestHeader(value="X-Wix-Site-Id",required=false) String siteId,
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam(defaultValue = "SESSIONS,UNIQUE_VISITORS") String metrics,
            @RequestParam(required = false) String dimensions) {
        QueryStatisticsRequest.QueryStatisticsRequestBuilder builder = QueryStatisticsRequest.builder()
                .dateRange(QueryStatisticsRequest.DateRange.builder().from(from).to(to).build())
                .metrics(List.of(metrics.split(",")));
        if (dimensions != null && !dimensions.isBlank()) {
            builder.dimensions(List.of(dimensions.split(",")));
        }
        return wix(apiKey, siteId).analytics().queryStatistics(builder.build());
    }

    private WixClient wix(String apiKey, String siteId) {
        return provider.get(apiKey, siteId);
    }
}
