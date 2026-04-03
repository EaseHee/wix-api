package com.wix.api.http;

public class WixRateLimitException extends WixApiException {

    public WixRateLimitException(String errorBody, WixErrorResponse errorResponse) {
        super(429, errorBody, errorResponse);
    }
}
