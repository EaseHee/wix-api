package com.wix.api.module.contacts.dto;

import lombok.Data;

@Data
public class Label {

    private String key;
    private String displayName;
    private String labelType;
    private String createdDate;
    private String updatedDate;
}
