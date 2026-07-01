package com.ecommerce.review.controller;

import com.ecommerce.common.exception.AuthorizationException;
import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.common.exception.ConflictException;
import com.ecommerce.common.exception.GlobalExceptionHandler;
import com.ecommerce.review.dto.ReviewAppendRequest;
import com.ecommerce.review.dto.ReviewCreateRequest;
import com.ecommerce.review.dto.ReviewListResponse;
import com.ecommerce.review.dto.ReviewResponse;
import com.ecommerce.review.entity.ReviewStatus;
import com.ecommerce.review.service.ReviewService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests for {@link ReviewController} using standalone MockMvc setup.
 */
@DisplayName("ReviewController")
class ReviewControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private ReviewService reviewService;

    private ReviewCreateRequest createRequest;
    private ReviewResponse reviewResponse;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        MappingJackson2HttpMessageConverter jacksonConverter = new MappingJackson2HttpMessageConverter();
        jacksonConverter.setObjectMapper(objectMapper);

        reviewService = mock(ReviewService.class);

        ReviewController controller = new ReviewController(reviewService);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(jacksonConverter)
                .build();

        setupMockAuthentication("1", "ROLE_USER");

        createRequest = new ReviewCreateRequest();
        createRequest.setProductId(100L);
        createRequest.setOrderId(200L);
        createRequest.setOrderItemId(300L);
        createRequest.setRating(5);
        createRequest.setContent("Great product!");

        reviewResponse = new ReviewResponse();
        reviewResponse.setId(1L);
        reviewResponse.setUserId(1L);
        reviewResponse.setProductId(100L);
        reviewResponse.setOrderId(200L);
        reviewResponse.setOrderItemId(300L);
        reviewResponse.setRating(5);
        reviewResponse.setContent("Great product!");
        reviewResponse.setStatus(ReviewStatus.PENDING_REVIEW);
        reviewResponse.setAppended(false);
        reviewResponse.setCreatedAt(LocalDateTime.now());
        reviewResponse.setUpdatedAt(LocalDateTime.now());
        reviewResponse.setAppends(Collections.emptyList());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void setupMockAuthentication(String username, String... roles) {
        List<SimpleGrantedAuthority> authorities = new java.util.ArrayList<>();
        for (String role : roles) {
            authorities.add(new SimpleGrantedAuthority(role));
        }
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn(username);
        when(auth.getAuthorities()).thenAnswer(inv -> authorities);
        when(auth.isAuthenticated()).thenReturn(true);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    // -----------------------------------------------------------------------
    // POST /api/v1/reviews
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("POST /api/v1/reviews")
    class CreateReview {

        @Test
        @DisplayName("createReview: returns 201 Created")
        void testCreateReview_returns201() throws Exception {
            setupMockAuthentication("1", "ROLE_USER");
            when(reviewService.createReview(eq(1L), any(ReviewCreateRequest.class)))
                    .thenReturn(reviewResponse);

            mockMvc.perform(post("/api/v1/reviews")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(createRequest)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.productId").value(100))
                    .andExpect(jsonPath("$.rating").value(5))
                    .andExpect(jsonPath("$.status").value("PENDING_REVIEW"));
        }

        @Test
        @DisplayName("createReview: purchase gate failure returns 403 REVIEW_PURCHASE_REQUIRED")
        void testCreateReview_purchaseGateFailure_returns403() throws Exception {
            setupMockAuthentication("1", "ROLE_USER");
            when(reviewService.createReview(eq(1L), any(ReviewCreateRequest.class)))
                    .thenThrow(new BusinessException("REVIEW_PURCHASE_REQUIRED",
                            "User must purchase and receive the product before creating a review"));

            mockMvc.perform(post("/api/v1/reviews")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(createRequest)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("REVIEW_PURCHASE_REQUIRED"));
        }

        @Test
        @DisplayName("createReview: duplicate review returns 409")
        void testCreateReview_duplicateReview_returns409() throws Exception {
            setupMockAuthentication("1", "ROLE_USER");
            when(reviewService.createReview(eq(1L), any(ReviewCreateRequest.class)))
                    .thenThrow(new ConflictException("You have already reviewed this order item"));

            mockMvc.perform(post("/api/v1/reviews")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(createRequest)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("CONFLICT"));
        }
    }

    // -----------------------------------------------------------------------
    // POST /api/v1/reviews/{reviewId}/append
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("POST /api/v1/reviews/{reviewId}/append")
    class AppendReview {

        @Test
        @DisplayName("appendReview: returns 201 Created")
        void testAppendReview_returns201() throws Exception {
            setupMockAuthentication("1", "ROLE_USER");
            reviewResponse.setAppended(true);
            when(reviewService.appendReview(eq(1L), eq(10L), any(ReviewAppendRequest.class)))
                    .thenReturn(reviewResponse);

            ReviewAppendRequest appendRequest = new ReviewAppendRequest();
            appendRequest.setContent("Updated my thoughts");

            mockMvc.perform(post("/api/v1/reviews/10/append")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(appendRequest)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.appended").value(true));
        }

        @Test
        @DisplayName("appendReview: non-owner returns 403")
        void testAppendReview_nonOwner_returns403() throws Exception {
            setupMockAuthentication("1", "ROLE_USER");
            when(reviewService.appendReview(eq(1L), eq(10L), any(ReviewAppendRequest.class)))
                    .thenThrow(AuthorizationException.forbidden("You can only append to your own reviews"));

            ReviewAppendRequest appendRequest = new ReviewAppendRequest();
            appendRequest.setContent("Updated my thoughts");

            mockMvc.perform(post("/api/v1/reviews/10/append")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(appendRequest)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }
    }

    // -----------------------------------------------------------------------
    // GET /api/v1/reviews/product/{productId}
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("GET /api/v1/reviews/product/{productId}")
    class GetProductReviews {

        @Test
        @DisplayName("getProductReviews: returns 200 OK (anonymous access)")
        void testGetProductReviews_anonymous_returns200() throws Exception {
            ReviewListResponse listResponse = new ReviewListResponse(
                    0, 10, 1, Collections.singletonList(reviewResponse));

            when(reviewService.getProductReviews(eq(100L), anyInt(), anyInt()))
                    .thenReturn(listResponse);

            mockMvc.perform(get("/api/v1/reviews/product/100")
                            .param("page", "0")
                            .param("size", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[0].id").value(1))
                    .andExpect(jsonPath("$.total").value(1))
                    .andExpect(jsonPath("$.averageRating").doesNotExist())
                    .andExpect(jsonPath("$.totalReviews").doesNotExist());
        }
    }

    // -----------------------------------------------------------------------
    // GET /api/v1/reviews/my
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("GET /api/v1/reviews/my")
    class GetMyReviews {

        @Test
        @DisplayName("getMyReviews: returns 200 OK")
        void testGetMyReviews_returns200() throws Exception {
            setupMockAuthentication("1", "ROLE_USER");
            ReviewListResponse listResponse = new ReviewListResponse(
                    0, 10, 1, Collections.singletonList(reviewResponse));

            when(reviewService.getMyReviews(eq(1L), anyInt(), anyInt()))
                    .thenReturn(listResponse);

            mockMvc.perform(get("/api/v1/reviews/my")
                            .param("page", "0")
                            .param("size", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[0].id").value(1))
                    .andExpect(jsonPath("$.total").value(1));
        }
    }
}
