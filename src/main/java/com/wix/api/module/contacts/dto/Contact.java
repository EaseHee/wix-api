package com.wix.api.module.contacts.dto;

import java.util.Map;

import lombok.Data;

@Data
public class Contact {

    private String id;
    private int revision;
    private ContactInfo info;
    private String createdDate;
    private String updatedDate;
    private Map<String, Object> source;
    private Map<String, Object> lastActivity;
    private Map<String, Object> primaryInfo;
    private Map<String, Object> picture;
}
