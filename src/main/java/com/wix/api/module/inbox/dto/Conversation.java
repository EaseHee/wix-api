package com.wix.api.module.inbox.dto;

import java.util.List;
import java.util.Map;

import lombok.Data;

@Data
public class Conversation {

    private String id;
    private Map<String, Object> participant;
    private List<String> channels;
    private String createdDate;
    private Map<String, Object> businessDisplayData;
    private Map<String, Object> participantDisplayData;
}
