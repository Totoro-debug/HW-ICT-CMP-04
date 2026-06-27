package com.ecommerce.user.service;

import com.ecommerce.common.exception.ConflictException;
import com.ecommerce.common.notification.NotificationChannel;
import com.ecommerce.common.notification.NotificationRequest;
import com.ecommerce.user.dto.RegisterRequest;
import com.ecommerce.user.dto.UserResponse;
import com.ecommerce.user.entity.User;
import com.ecommerce.user.entity.UserRole;
import com.ecommerce.user.entity.UserStatus;
import com.ecommerce.user.event.UserRegistrationNotificationEvent;
import com.ecommerce.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Handles user registration.
 */
@Service
public class UserRegisterService {

    private static final Logger log = LoggerFactory.getLogger(UserRegisterService.class);

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;

    public UserRegisterService(UserRepository userRepository,
                               BCryptPasswordEncoder passwordEncoder,
                               ApplicationEventPublisher eventPublisher) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        // Check uniqueness
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ConflictException("Email already registered: " + request.getEmail());
        }
        if (userRepository.existsByPhone(request.getPhone())) {
            throw new ConflictException("Phone already registered: " + request.getPhone());
        }

        User user = new User();
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setNickname(request.getNickname());
        user.setStatus(UserStatus.ACTIVE);
        user.setRole(UserRole.USER);

        User saved = userRepository.save(user);
        log.info("User registered: id={}, email={}, status={}", saved.getId(), saved.getEmail(), saved.getStatus());

        eventPublisher.publishEvent(new UserRegistrationNotificationEvent(this, saved.getId(), saved.getEmail(),
                buildRegistrationNotification(saved)));

        return UserResponse.from(saved);
    }

    private NotificationRequest buildRegistrationNotification(User user) {
        return NotificationRequest.builder()
                .bizType("USER_REGISTERED")
                .bizId(String.valueOf(user.getId()))
                .receiver(user.getEmail())
                .channel(NotificationChannel.EMAIL)
                .templateCode("USER_REGISTERED")
                .variables(Map.of(
                        "userId", user.getId(),
                        "email", user.getEmail(),
                        "nickname", user.getNickname() == null ? "" : user.getNickname()))
                .idempotencyKey("USER_REGISTERED:" + user.getId())
                .build();
    }
}
