package com.wix.api.module.members.dto;

import java.util.List;
import java.util.Map;

import com.wix.api.common.PagingRequest;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class QueryMembersRequest {

    private Map<String, Object> filter;
    private List<Map<String, String>> sort;
    private PagingRequest paging;
    private List<String> fieldsets;
}
