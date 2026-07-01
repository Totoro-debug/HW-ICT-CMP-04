package com.ecommerce.common.security;

import com.ecommerce.common.dto.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.UUID;

/**
 * Writes standard API errors for failures raised inside the security filter chain.
 */
@Component
public class ApiSecurityErrorWriter {

    private final ObjectMapper objectMapper;

    public ApiSecurityErrorWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void writeUnauthorized(HttpServletRequest request, HttpServletResponse response, Exception exception) throws IOException {
        write(response, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Unauthorized");
    }

    public void writeForbidden(HttpServletRequest request, HttpServletResponse response, Exception exception) throws IOException {
        write(response, HttpStatus.FORBIDDEN, "FORBIDDEN", "Forbidden");
    }

    private void write(HttpServletResponse response, HttpStatus status, String code, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        ApiError error = new ApiError(code, message, generateTraceId(), new HashMap<>());
        objectMapper.writeValue(response.getWriter(), error);
    }

    private String generateTraceId() {
        return UUID.randomUUID().toString().substring(0, 8) + "-" + System.currentTimeMillis();
    }
}
