package com.wix.api.common;

import lombok.Data;

@Data
public class CursorPagingMetadata {

    private int count;
    private Cursors cursors;

    @Data
    public static class Cursors {
        private String next;
        private String prev;
    }
}
