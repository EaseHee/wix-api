package com.wix.api.module.cms.dto;

import java.util.List;
import java.util.Map;

import lombok.Data;

@Data
public class DataCollection {

    private String id;
    private String displayName;
    private List<Field> fields;
    private Map<String, Object> permissions;
    private String revision;
    private String createdDate;
    private String updatedDate;

    @Data
    public static class Field {
        private String key;
        private String displayName;
        private String type;
        private boolean required;
        private Map<String, Object> typeConfig;
    }
}
