package com.wix.api.http;

import lombok.Data;

@Data
public class WixErrorResponse {

    private String message;
    private Details details;

    @Data
    public static class Details {
        private String applicationError;
        private String validationError;
    }
}
