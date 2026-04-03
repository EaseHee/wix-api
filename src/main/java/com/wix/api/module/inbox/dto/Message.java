package com.wix.api.module.inbox.dto;

import java.util.List;
import java.util.Map;

import lombok.Data;

@Data
public class Message {

    private String id;
    private String sequence;
    private String direction;
    private String visibility;
    private String createdDate;
    private String sourceChannel;
    private Map<String, Object> content;
    private Map<String, Object> sender;
    private List<String> targetChannels;
    private List<String> targetChannelIds;
    private List<Map<String, Object>> badges;
    private boolean silent;
    private String appId;
}
