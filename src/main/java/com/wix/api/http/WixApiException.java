package com.wix.api.http;

import lombok.Getter;

@Getter
public class WixApiException extends RuntimeException {

    private final int statusCode;
    private final String errorBody;
    private final WixErrorResponse errorResponse;

    public WixApiException(int statusCode, String errorBody, WixErrorResponse errorResponse) {
        super("Wix API error [" + statusCode + "]: " +
                (errorResponse != null ? errorResponse.getMessage() : errorBody));
        this.statusCode = statusCode;
        this.errorBody = errorBody;
        this.errorResponse = errorResponse;
    }
}
