package com.ecommerce.app.controller;

import com.ecommerce.app.CorsConfig;
import com.ecommerce.app.SecurityConfig;
import com.ecommerce.common.audit.AuditLogItem;
import com.ecommerce.common.security.ApiSecurityErrorWriter;
import com.ecommerce.common.audit.AuditLogQuery;
import com.ecommerce.common.audit.AuditLogService;
import com.ecommerce.common.event.FailedEventRecordQueryService;
import com.ecommerce.common.event.FailedEventReplayResult;
import com.ecommerce.common.event.FailedEventReplayService;
import com.ecommerce.common.event.FailedEventStatus;
import com.ecommerce.common.exception.GlobalExceptionHandler;
import com.ecommerce.user.service.JwtTokenProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.DispatcherServletAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.ServletWebServerFactoryAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.error.ErrorMvcAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = AdminControllerTest.TestConfig.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "security.jwt.secret=this-is-a-very-long-secret-key-for-testing-purposes-only",
        "security.jwt.issuer=test-issuer",
        "security.jwt.expire-minutes=60"
})
@DisplayName("Admin management controllers")
class AdminControllerTest {

    @org.springframework.boot.SpringBootConfiguration
    @ImportAutoConfiguration({
            WebMvcAutoConfiguration.class,
            DispatcherServletAutoConfiguration.class,
            ServletWebServerFactoryAutoConfiguration.class,
            ErrorMvcAutoConfiguration.class,
            JacksonAutoConfiguration.class,
            HttpMessageConvertersAutoConfiguration.class,
            SecurityAutoConfiguration.class,
            SecurityFilterAutoConfiguration.class
    })
    @Import({
            SecurityConfig.class,
            CorsConfig.class,
            ApiSecurityErrorWriter.class,
            JwtTokenProvider.class,
            GlobalExceptionHandler.class,
            EventFailureAdminController.class,
            AuditAdminController.class
    })
    static class TestConfig {
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private FailedEventRecordQueryService failedEventRecordQueryService;

    @MockBean
    private FailedEventReplayService failedEventReplayService;

    @MockBean
    private AuditLogService auditLogService;

    private String adminAuthHeader() {
        return "Bearer " + jwtTokenProvider.generateToken(1L, List.of("ADMIN"));
    }

    private String userAuthHeader() {
        return "Bearer " + jwtTokenProvider.generateToken(2L, List.of("USER"));
    }

    @Test
    @DisplayName("ADMIN can replay failed event by id")
    void replayFailure_admin_returnsReplayResult() throws Exception {
        when(failedEventReplayService.replay(10L))
                .thenReturn(new FailedEventReplayResult(10L, FailedEventStatus.SUCCEEDED, 2, null));

        mockMvc.perform(post("/api/v1/admin/events/failures/10/replay")
                        .header("Authorization", adminAuthHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recordId").value(10))
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.retryCount").value(2));

        verify(failedEventReplayService).replay(10L);
    }

    @Test
    @DisplayName("ADMIN can replay pending failed events by condition")
    void replayPendingFailures_admin_returnsReplayResults() throws Exception {
        when(failedEventReplayService.replayPending("OrderCreatedEvent"))
                .thenReturn(List.of(new FailedEventReplayResult(11L, FailedEventStatus.FAILED, 1, "boom")));

        mockMvc.perform(post("/api/v1/admin/events/failures/replay")
                        .param("eventType", "OrderCreatedEvent")
                        .header("Authorization", adminAuthHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.records[0].recordId").value(11))
                .andExpect(jsonPath("$.records[0].status").value("FAILED"))
                .andExpect(jsonPath("$.records[0].errorMessage").value("boom"));

        verify(failedEventReplayService).replayPending("OrderCreatedEvent");
    }

    @Test
    @DisplayName("failed event replay endpoint returns standard 403 body for USER role")
    void replayFailure_userRole_forbidden() throws Exception {
        mockMvc.perform(post("/api/v1/admin/events/failures/10/replay")
                        .header("Authorization", userAuthHeader()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(jsonPath("$.details").isMap());
    }

    @Test
    @DisplayName("ADMIN can query audit logs")
    void getAuditLogs_admin_returnsRecords() throws Exception {
        LocalDateTime operatedAt = LocalDateTime.of(2026, 6, 28, 10, 0);
        when(auditLogService.find(any(AuditLogQuery.class)))
                .thenReturn(List.of(new AuditLogItem(3L, "1", "admin", "USER_FREEZE",
                        "USER", "42", "ACTIVE", "FROZEN", operatedAt, "manual freeze")));

        mockMvc.perform(get("/api/v1/admin/audit-logs")
                        .param("operatorId", "1")
                        .param("operationType", "USER_FREEZE")
                        .param("bizType", "USER")
                        .param("bizId", "42")
                        .header("Authorization", adminAuthHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.records[0].operatorId").value("1"))
                .andExpect(jsonPath("$.records[0].operatorName").value("admin"))
                .andExpect(jsonPath("$.records[0].operationType").value("USER_FREEZE"))
                .andExpect(jsonPath("$.records[0].bizId").value("42"))
                .andExpect(jsonPath("$.records[0].beforeState").value("ACTIVE"))
                .andExpect(jsonPath("$.records[0].afterState").value("FROZEN"))
                .andExpect(jsonPath("$.records[0].remark").value("manual freeze"));

        verify(auditLogService).find(any(AuditLogQuery.class));
    }

    @Test
    @DisplayName("audit log endpoint returns standard 403 body for USER role")
    void getAuditLogs_userRole_forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/admin/audit-logs")
                        .header("Authorization", userAuthHeader()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(jsonPath("$.details").isMap());
    }
}
