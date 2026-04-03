package com.wix.api.module.seo.dto;

import java.util.List;

import lombok.Data;

@Data
public class RobotsTxt {

    private List<Rule> rules;
    private List<String> sitemaps;

    @Data
    public static class Rule {
        private String userAgent;
        private List<String> allow;
        private List<String> disallow;
    }
}
