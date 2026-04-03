package com.wix.api.common;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PagingRequest {

    @Builder.Default
    private int limit = 50;

    @Builder.Default
    private int offset = 0;
}
