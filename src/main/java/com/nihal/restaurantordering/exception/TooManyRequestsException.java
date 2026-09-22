package com.nihal.restaurantordering.exception;

public class TooManyRequestsException extends ApiException {
    public TooManyRequestsException(String message) {
        super(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS, message);
    }
}
