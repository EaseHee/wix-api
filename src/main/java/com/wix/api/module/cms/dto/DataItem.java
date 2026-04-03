package com.wix.api.module.cms.dto;

import java.util.Map;

import lombok.Data;

@Data
public class DataItem {

    private String id;
    private Map<String, Object> data;
    private String createdDate;
    private String updatedDate;
}
