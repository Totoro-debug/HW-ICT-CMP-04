package com.ecommerce.user.event;

import com.ecommerce.common.event.AbstractDomainEvent;

/**
 * Event published after a user registration succeeds.
 * Common notification listens to this event by simpleName and reflective fields.
 */
public class UserRegisteredEvent extends AbstractDomainEvent {

    private final Long userId;
    private final String email;
    private final String nickname;

    public UserRegisteredEvent(Object source, Long userId, String email, String nickname) {
        super(source);
        this.userId = userId;
        this.email = email;
        this.nickname = nickname;
    }

    public Long getUserId() {
        return userId;
    }

    public String getEmail() {
        return email;
    }

    public String getNickname() {
        return nickname;
    }
}
