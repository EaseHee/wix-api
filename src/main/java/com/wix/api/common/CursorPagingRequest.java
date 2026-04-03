package com.wix.api.common;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CursorPagingRequest {

    @Builder.Default
    private int limit = 50;

    private String cursor;
}
