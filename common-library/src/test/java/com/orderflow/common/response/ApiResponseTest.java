package com.orderflow.common.response;

import com.orderflow.common.correlation.CorrelationIdConstants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ApiResponseTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void successPopulatesDataAndDefaultsMessage() {
        ApiResponse<String> response = ApiResponse.success("payload");

        assertThat(response.success()).isTrue();
        assertThat(response.data()).isEqualTo("payload");
        assertThat(response.message()).isEqualTo("Request processed successfully");
        assertThat(response.errors()).isEmpty();
        assertThat(response.timestamp()).isNotNull();
    }

    @Test
    void successWithCustomMessage() {
        ApiResponse<Integer> response = ApiResponse.success(42, "Order created");

        assertThat(response.message()).isEqualTo("Order created");
        assertThat(response.data()).isEqualTo(42);
    }

    @Test
    void errorHasNoDataAndCarriesErrorList() {
        ApiResponse<Void> response = ApiResponse.error("Validation failed", List.of(new ApiError("email", "must not be blank")));

        assertThat(response.success()).isFalse();
        assertThat(response.data()).isNull();
        assertThat(response.errors()).hasSize(1);
        assertThat(response.errors().get(0).field()).isEqualTo("email");
    }

    @Test
    void correlationIdDefaultsToEmptyStringWhenMdcNotSet() {
        ApiResponse<String> response = ApiResponse.success("x");

        assertThat(response.correlationId()).isEmpty();
    }

    @Test
    void correlationIdIsReadFromMdcWhenPresent() {
        MDC.put(CorrelationIdConstants.MDC_KEY, "abc-123");

        ApiResponse<String> response = ApiResponse.success("x");

        assertThat(response.correlationId()).isEqualTo("abc-123");
    }
}
