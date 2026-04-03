package com.wix.api.module.analytics.dto;

import java.util.Map;

import lombok.Data;

@Data
public class StatisticsEntry {

    private Map<String, String> dimensionValues;
    private Map<String, Object> metricValues;
}
