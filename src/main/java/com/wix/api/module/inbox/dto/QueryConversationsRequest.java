package com.wix.api.module.inbox.dto;

import java.util.List;
import java.util.Map;

import com.wix.api.common.CursorPagingRequest;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class QueryConversationsRequest {

    private Map<String, Object> filter;
    private List<Map<String, String>> sort;
    private CursorPagingRequest cursorPaging;
}
