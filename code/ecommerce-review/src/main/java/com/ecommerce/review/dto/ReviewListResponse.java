package com.ecommerce.review.dto;

import com.ecommerce.common.dto.PageResponse;

/**
 * Response DTO for a paginated list of reviews.
 * Uses the standard pagination fields: page, size, total, items.
 */
public class ReviewListResponse extends PageResponse<ReviewResponse> {

    public ReviewListResponse() {
    }

    public ReviewListResponse(int page, int size, long total, java.util.List<ReviewResponse> items) {
        super(page, size, total, items);
    }
}
