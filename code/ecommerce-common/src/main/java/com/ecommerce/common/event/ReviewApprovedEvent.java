package com.ecommerce.common.event;

/**
 * Domain event published when a review is approved by an admin.
 * The loyalty module listens for this event to award review reward points.
 */
public class ReviewApprovedEvent extends AbstractDomainEvent {

    private final Long reviewId;
    private final Long userId;
    private final Long orderId;
    private final Long productId;

    public ReviewApprovedEvent(Object source, Long reviewId, Long userId) {
        this(source, reviewId, userId, null, null);
    }

    public ReviewApprovedEvent(Object source, Long reviewId, Long userId,
                               Long orderId, Long productId) {
        super(source, "ReviewApprovedEvent", reviewId == null ? null : String.valueOf(reviewId), null);
        this.reviewId = reviewId;
        this.userId = userId;
        this.orderId = orderId;
        this.productId = productId;
    }

    public Long getReviewId() {
        return reviewId;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getOrderId() {
        return orderId;
    }

    public Long getProductId() {
        return productId;
    }
}
