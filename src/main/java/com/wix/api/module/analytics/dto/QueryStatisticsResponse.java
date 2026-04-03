package com.wix.api.module.analytics.dto;

import java.util.List;

import lombok.Data;

@Data
public class QueryStatisticsResponse {

    private List<StatisticsEntry> data;
}
