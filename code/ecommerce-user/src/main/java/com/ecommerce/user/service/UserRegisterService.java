package com.ecommerce.user.service;

import com.ecommerce.common.exception.ConflictException;
import com.ecommerce.common.notification.NotificationChannel;
import com.ecommerce.common.notification.NotificationRequest;
import com.ecommerce.user.dto.RegisterRequest;
import com.ecommerce.user.dto.UserResponse;
import com.ecommerce.user.entity.EmailActivationToken;
import com.ecommerce.user.entity.User;
import com.ecommerce.user.entity.UserProfile;
import com.ecommerce.user.entity.UserRole;
import com.ecommerce.user.entity.UserStatus;
import com.ecommerce.user.event.UserRegistrationNotificationEvent;
import com.ecommerce.user.repository.EmailActivationTokenRepository;
import com.ecommerce.user.repository.UserProfileRepository;
import com.ecommerce.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Handles user registration.
 */
@Service
public class UserRegisterService {

    private static final Logger log = LoggerFactory.getLogger(UserRegisterService.class);
    private static final long ACTIVATION_TOKEN_EXPIRE_HOURS = 24L;
    private static final String ACTIVATION_TEMPLATE = "EMAIL_ACTIVATION";

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final EmailActivationTokenRepository emailActivationTokenRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;

    public UserRegisterService(UserRepository userRepository,
                               UserProfileRepository userProfileRepository,
                               EmailActivationTokenRepository emailActivationTokenRepository,
                               BCryptPasswordEncoder passwordEncoder,
                               ApplicationEventPublisher eventPublisher) {
        this.userRepository = userRepository;
        this.userProfileRepository = userProfileRepository;
        this.emailActivationTokenRepository = emailActivationTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
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
        user.setStatus(UserStatus.PENDING_ACTIVATION);
        user.setRole(UserRole.USER);

        User saved = userRepository.save(user);
        saveUserProfile(saved);
        EmailActivationToken activationToken = saveActivationToken(saved.getId());
        log.info("User registered: id={}, email={}, status={}", saved.getId(), saved.getEmail(), saved.getStatus());

        eventPublisher.publishEvent(new UserRegistrationNotificationEvent(this, saved.getId(), saved.getEmail(),
                buildRegistrationNotification(saved, activationToken)));

        return UserResponse.from(saved);
    }

    private void saveUserProfile(User user) {
        UserProfile profile = new UserProfile();
        profile.setUserId(user.getId());
        profile.setNickname(user.getNickname());
        userProfileRepository.save(profile);
    }

    private EmailActivationToken saveActivationToken(Long userId) {
        EmailActivationToken activationToken = new EmailActivationToken();
        activationToken.setUserId(userId);
        activationToken.setToken(UUID.randomUUID().toString().replace("-", ""));
        activationToken.setExpiresAt(LocalDateTime.now().plusHours(ACTIVATION_TOKEN_EXPIRE_HOURS));
        activationToken.setUsed(false);
        return emailActivationTokenRepository.save(activationToken);
    }

    private NotificationRequest buildRegistrationNotification(User user, EmailActivationToken activationToken) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("userId", user.getId());
        variables.put("email", user.getEmail());
        variables.put("nickname", user.getNickname() == null ? "" : user.getNickname());
        variables.put("activationToken", activationToken.getToken());
        variables.put("activationLink", "/api/v1/users/activate?token=" + activationToken.getToken());

        NotificationRequest request = new NotificationRequest();
        request.setBizType(ACTIVATION_TEMPLATE);
        request.setBizId(String.valueOf(user.getId()));
        request.setReceiver(user.getEmail());
        request.setChannel(NotificationChannel.EMAIL);
        request.setTemplateCode(ACTIVATION_TEMPLATE);
        request.setVariables(variables);
        request.setIdempotencyKey(ACTIVATION_TEMPLATE + ":" + user.getId());
        return request;
    }
}
