package com.ecommerce.user.entity;

import com.ecommerce.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * User account entity holding core authentication and identity fields.
 */
@Entity
@Table(name = "users")
public class User extends BaseEntity {

    @Column(nullable = false, unique = true, length = 128)
    private String email;

    @Column(nullable = false, unique = true, length = 20)
    private String phone;

    @Column(name = "password_hash", nullable = false, length = 256)
    private String passwordHash;

    @Column(nullable = false, length = 64)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private UserStatus status;

    @Column(name = "roles", nullable = false, length = 128)
    private String roles;

    public User() {
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public UserStatus getStatus() {
        return status;
    }

    public void setStatus(UserStatus status) {
        this.status = status;
    }

    public String getRoles() {
        return roles;
    }

    public void setRoles(String roles) {
        this.roles = roles;
    }

    public List<String> getRoleList() {
        if (roles == null || roles.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(roles.split(","))
                .map(String::trim)
                .filter(role -> !role.isEmpty())
                .collect(Collectors.toList());
    }

    public void setRoleList(List<String> roles) {
        if (roles == null || roles.isEmpty()) {
            this.roles = null;
            return;
        }
        this.roles = roles.stream()
                .map(String::trim)
                .filter(role -> !role.isEmpty())
                .collect(Collectors.joining(","));
    }

    public UserRole getRole() {
        return getRoleList().stream()
                .findFirst()
                .map(UserRole::valueOf)
                .orElse(null);
    }

    public void setRole(UserRole role) {
        this.roles = role == null ? null : role.name();
    }
}
