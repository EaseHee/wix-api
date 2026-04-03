package com.wix.api.common;

import lombok.Data;

@Data
public class PagingMetadata {

    private int count;
    private int offset;
    private Integer total;
    private boolean hasNext;
}
