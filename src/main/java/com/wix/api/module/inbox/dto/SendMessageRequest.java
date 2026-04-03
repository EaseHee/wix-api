package com.wix.api.module.inbox.dto;

import java.util.Map;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SendMessageRequest {

    private String direction;
    private Map<String, Object> content;
}
