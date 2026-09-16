package com.bloodlink.controller;

import com.bloodlink.dto.ReviewDTO;
import com.bloodlink.dto.ReviewRequest;
import com.bloodlink.security.AuthenticatedUserService;
import com.bloodlink.service.ReviewService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/api/reviews")
public class ReviewController {

    private final ReviewService reviewService;
    private final AuthenticatedUserService authenticatedUserService;

    public ReviewController(ReviewService reviewService) {
        this(reviewService, new AuthenticatedUserService());
    }

    @Autowired
    public ReviewController(ReviewService reviewService, AuthenticatedUserService authenticatedUserService) {
        this.reviewService = reviewService;
        this.authenticatedUserService = authenticatedUserService != null ? authenticatedUserService : new AuthenticatedUserService();
    }

    @GetMapping
    public ResponseEntity<?> getAllReviews() {
        try {
            List<ReviewDTO> reviews = reviewService.getAllReviews();
            return ResponseEntity.ok(reviews);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Collections.singletonMap("error", "Failed to retrieve reviews."));
        }
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<?> getReviewByUser(@PathVariable("userId") String userId) {
        try {
            return reviewService.getReviewByUserId(userId)
                    .<ResponseEntity<?>>map(ResponseEntity::ok)
                    .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                            .body(Collections.singletonMap("error", "Review not found for this user.")));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Collections.singletonMap("error", "Failed to retrieve user review."));
        }
    }

    @PostMapping
    public ResponseEntity<?> submitReview(@RequestBody ReviewRequest request) {
        try {
            String authUserId = authenticatedUserService.getAuthenticatedUserId();
            if (authUserId != null) {
                if (request != null && request.getUserId() != null && !request.getUserId().trim().isEmpty()
                        && !authUserId.equals(request.getUserId().trim())) {
                    throw new AccessDeniedException("Forbidden: Cannot create review for another user");
                }
                if (request == null) {
                    request = new ReviewRequest();
                }
                request.setUserId(authUserId);
            }
            if (request == null || request.getUserId() == null || request.getUserId().trim().isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Collections.singletonMap("error", "User ID is required."));
            }
            ReviewDTO review = reviewService.createOrUpdateReview(request.getUserId(), request);
            return ResponseEntity.ok(review);
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Collections.singletonMap("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            String msg = e.getMessage();
            if ("USER_NOT_FOUND".equals(msg)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Collections.singletonMap("error", "User not found."));
            } else if ("MISSING_TEXT".equals(msg)) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Collections.singletonMap("error", "Review text is required."));
            } else if ("INVALID_RATING".equals(msg)) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Collections.singletonMap("error", "Rating must be between 1 and 5."));
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Collections.singletonMap("error", msg));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Collections.singletonMap("error", "Failed to submit review."));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteReview(@PathVariable("id") String id, @RequestParam(value = "userId", required = false) String userId) {
        try {
            String authUserId = authenticatedUserService.getAuthenticatedUserId();
            if (authUserId != null) {
                if (userId != null && !userId.trim().isEmpty() && !authUserId.equals(userId.trim())) {
                    throw new AccessDeniedException("Forbidden: You do not own this review");
                }
                userId = authUserId;
            }
            if (userId == null || userId.trim().isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Collections.singletonMap("error", "User ID is required to delete review."));
            }
            reviewService.deleteReview(id, userId);
            return ResponseEntity.ok(Collections.singletonMap("message", "Review deleted successfully."));
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Collections.singletonMap("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            String msg = e.getMessage();
            if ("REVIEW_NOT_FOUND".equals(msg)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Collections.singletonMap("error", "Review not found."));
            } else if ("UNAUTHORIZED".equals(msg)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Collections.singletonMap("error", "You are not authorized to delete this review."));
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Collections.singletonMap("error", msg));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Collections.singletonMap("error", "Failed to delete review."));
        }
    }

    @DeleteMapping("/user/{userId}")
    public ResponseEntity<?> deleteReviewByUser(@PathVariable("userId") String userId) {
        try {
            authenticatedUserService.requireCurrentUser(userId);
            reviewService.deleteReviewByUserId(userId);
            return ResponseEntity.ok(Collections.singletonMap("message", "Review deleted successfully."));
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Collections.singletonMap("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            String msg = e.getMessage();
            if ("REVIEW_NOT_FOUND".equals(msg)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Collections.singletonMap("error", "Review not found for this user."));
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Collections.singletonMap("error", msg));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Collections.singletonMap("error", "Failed to delete review."));
        }
    }
}