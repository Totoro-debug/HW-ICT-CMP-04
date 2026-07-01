package com.ecommerce.app;

import com.ecommerce.common.security.ApiSecurityErrorWriter;
import com.ecommerce.user.cache.UserRoleCacheManager;
import com.ecommerce.user.security.JwtAuthFilter;
import com.ecommerce.user.service.JwtTokenProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Central Spring Security configuration for the ShopHub application.
 * Defines the security filter chain with JWT-based authentication,
 * role-based authorization, and stateless session management.
 */
@Configuration("appSecurityConfig")
@EnableWebSecurity
public class SecurityConfig {

    private final JwtTokenProvider jwtTokenProvider;
    private final ApiSecurityErrorWriter apiSecurityErrorWriter;

    public SecurityConfig(JwtTokenProvider jwtTokenProvider, ApiSecurityErrorWriter apiSecurityErrorWriter) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.apiSecurityErrorWriter = apiSecurityErrorWriter;
    }

    /**
     * Provides a BCryptPasswordEncoder bean for password hashing.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Configures the HTTP security filter chain:
     * - Disables CSRF (API uses JWT tokens)
     * - Stateless session management (no HTTP sessions)
     * - Public endpoints do not require authentication
     * - Admin endpoints require ADMIN role
     * - Other API endpoints require USER role
     * - JWT filter runs before the standard authentication filter
     */
    @Bean("appSecurityFilterChain")
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   ObjectProvider<UserRoleCacheManager> userRoleCacheManagerProvider) throws Exception {
        UserRoleCacheManager userRoleCacheManager = userRoleCacheManagerProvider.getIfAvailable(UserRoleCacheManager::new);
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(apiSecurityErrorWriter::writeUnauthorized)
                        .accessDeniedHandler(apiSecurityErrorWriter::writeForbidden))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/users/register").permitAll()
                        .requestMatchers("/api/v1/users/activate").permitAll()
                        .requestMatchers("/api/v1/users/login").permitAll()
                        .requestMatchers("/api/v1/products/**").permitAll()
                        .requestMatchers("/api/v1/inventory/**").permitAll()
                        .requestMatchers("/api/v1/categories/**").permitAll()
                        .requestMatchers("/api/v1/payment/callback").permitAll()
                        .requestMatchers("/api/v1/logistics/callback").permitAll()
                        .requestMatchers("/api/v1/reviews/product/**").permitAll()
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/**").hasRole("USER")
                        .anyRequest().permitAll()
                )
                .addFilterBefore(new JwtAuthFilter(jwtTokenProvider, userRoleCacheManager),
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
