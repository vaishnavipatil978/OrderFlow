package com.orderflow.orderservice;

import feign.FeignException;
import feign.Request;
import feign.Response;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Builds the Feign exceptions a real downstream error response would produce. */
public final class FeignTestErrors {

    private FeignTestErrors() {
    }

    public static FeignException status(int status) {
        Request request = Request.create(Request.HttpMethod.POST, "http://test", Map.of(), null, StandardCharsets.UTF_8, null);
        Response response = Response.builder().status(status).reason("test").request(request).build();
        return FeignException.errorStatus("test#call", response);
    }
}
