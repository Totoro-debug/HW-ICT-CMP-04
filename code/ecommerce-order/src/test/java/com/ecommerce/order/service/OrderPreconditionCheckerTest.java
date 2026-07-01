package com.ecommerce.order.service;

import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.common.test.RuntimeConfigRegistry;
import com.ecommerce.order.config.OrderProperties;
import com.ecommerce.user.query.UserDto;
import com.ecommerce.user.query.UserQueryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link OrderPreconditionChecker}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OrderPreconditionChecker")
class OrderPreconditionCheckerTest {

    @AfterEach
    void tearDown() {
        RuntimeConfigRegistry.clear();
    }

    @Mock
    private UserQueryService userQueryService;

    @InjectMocks
    private OrderPreconditionChecker preconditionChecker;

    @Test
    @DisplayName("check passes when user exists and is ACTIVE")
    void testCheck_userExists_passes() {
        UserDto user = new UserDto();
        user.setUserId(1L);
        user.setStatus("ACTIVE");

        when(userQueryService.getUserById(1L)).thenReturn(user);

        assertThatCode(() -> preconditionChecker.check(1L, 3))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("check rejects frozen user with USER_FROZEN")
    void testCheck_userFrozen_throwsUserFrozen() {
        UserDto frozenUser = new UserDto();
        frozenUser.setUserId(2L);
        frozenUser.setStatus("FROZEN");

        when(userQueryService.getUserById(2L)).thenReturn(frozenUser);

        assertThatThrownBy(() -> preconditionChecker.check(2L, 1))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("User is frozen")
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo("USER_FROZEN");
    }

    @Test
    @DisplayName("check rejects non-active user with USER_NOT_ACTIVE")
    void testCheck_userNotActive_throwsUserNotActive() {
        UserDto pendingUser = new UserDto();
        pendingUser.setUserId(4L);
        pendingUser.setStatus("PENDING_ACTIVATION");

        when(userQueryService.getUserById(4L)).thenReturn(pendingUser);

        assertThatThrownBy(() -> preconditionChecker.check(4L, 1))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("not active")
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo("USER_NOT_ACTIVE");
    }

    @Test
    @DisplayName("check throws RESOURCE_NOT_FOUND when user is null")
    void testCheck_userNull_throwsException() {
        when(userQueryService.getUserById(99L)).thenReturn(null);

        assertThatThrownBy(() -> preconditionChecker.check(99L, 2))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("User not found")
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo("RESOURCE_NOT_FOUND");
    }

    @Test
    @DisplayName("check throws VALIDATION_FAILED when itemCount is zero or negative")
    void testCheck_emptyItems_throwsException() {
        UserDto user = new UserDto();
        user.setUserId(3L);
        user.setStatus("ACTIVE");
        when(userQueryService.getUserById(3L)).thenReturn(user);

        assertThatThrownBy(() -> preconditionChecker.check(3L, 0))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("at least one item")
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo("VALIDATION_FAILED");

        assertThatThrownBy(() -> preconditionChecker.check(3L, -1))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("at least one item")
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo("VALIDATION_FAILED");
    }

    @Test
    @DisplayName("check throws VALIDATION_FAILED when itemCount exceeds configured max")
    void testCheck_exceedsConfiguredMax_throwsException() {
        UserDto user = new UserDto();
        user.setUserId(5L);
        user.setStatus("ACTIVE");
        when(userQueryService.getUserById(5L)).thenReturn(user);
        OrderProperties orderProperties = new OrderProperties();
        orderProperties.setMaxItems(30);
        OrderPreconditionChecker configuredChecker = new OrderPreconditionChecker(userQueryService, orderProperties);
        RuntimeConfigRegistry.put("order.max-items", 2);

        assertThatThrownBy(() -> configuredChecker.check(5L, 3))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("at most 2 distinct items")
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo("VALIDATION_FAILED");
    }
}
