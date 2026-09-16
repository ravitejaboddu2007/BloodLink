package com.bloodlink.service;

import com.bloodlink.dto.ReviewDTO;
import com.bloodlink.dto.ReviewRequest;
import com.bloodlink.entity.Review;
import com.bloodlink.entity.User;
import com.bloodlink.repository.ReviewRepository;
import com.bloodlink.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final UserRepository userRepository;

    @Autowired
    public ReviewService(ReviewRepository reviewRepository, UserRepository userRepository) {
        this.reviewRepository = reviewRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<ReviewDTO> getAllReviews() {
        List<Review> reviews = reviewRepository.findAllByOrderByCreatedAtDesc();
        List<ReviewDTO> dtos = new ArrayList<>();
        for (Review r : reviews) {
            dtos.add(toDTO(r));
        }
        return dtos;
    }

    @Transactional(readOnly = true)
    public Optional<ReviewDTO> getReviewByUserId(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            return Optional.empty();
        }
        return reviewRepository.findByUserId(userId.trim()).map(this::toDTO);
    }

    @Transactional
    public ReviewDTO createOrUpdateReview(String userId, ReviewRequest req) {
        if (userId == null || userId.trim().isEmpty()) {
            throw new IllegalArgumentException("USER_NOT_FOUND");
        }
        if (req == null) {
            throw new IllegalArgumentException("MISSING_DATA");
        }

        String targetUserId = userId.trim();
        User user = userRepository.findById(targetUserId)
                .orElseThrow(() -> new IllegalArgumentException("USER_NOT_FOUND"));

        String text = req.getText();
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("MISSING_TEXT");
        }
        text = text.trim();

        Integer rating = req.getRating();
        if (rating == null) {
            rating = 5;
        }
        if (rating < 1 || rating > 5) {
            throw new IllegalArgumentException("INVALID_RATING");
        }

        Optional<Review> existingOpt = reviewRepository.findByUserId(user.getId());
        Review review;
        if (existingOpt.isPresent()) {
            review = existingOpt.get();
            review.setRole(user.getRole());
            review.setName(user.getName());
            review.setRating(rating);
            review.setText(text);
            review.setCreatedAt(LocalDateTime.now());
        } else {
            review = new Review();
            review.setId("rev_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
            review.setUserId(user.getId());
            review.setRole(user.getRole());
            review.setName(user.getName());
            review.setRating(rating);
            review.setText(text);
            review.setCreatedAt(LocalDateTime.now());
        }

        Review saved = reviewRepository.save(review);
        return toDTO(saved);
    }

    @Transactional
    public void deleteReview(String reviewId, String userId) {
        if (reviewId == null || reviewId.trim().isEmpty()) {
            throw new IllegalArgumentException("REVIEW_NOT_FOUND");
        }
        Review review = reviewRepository.findById(reviewId.trim())
                .orElseThrow(() -> new IllegalArgumentException("REVIEW_NOT_FOUND"));

        if (userId == null || !review.getUserId().equals(userId.trim())) {
            throw new IllegalArgumentException("UNAUTHORIZED");
        }
        reviewRepository.delete(review);
    }

    @Transactional
    public void deleteReviewByUserId(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            throw new IllegalArgumentException("USER_NOT_FOUND");
        }
        Review review = reviewRepository.findByUserId(userId.trim())
                .orElseThrow(() -> new IllegalArgumentException("REVIEW_NOT_FOUND"));
        reviewRepository.delete(review);
    }

    private ReviewDTO toDTO(Review r) {
        return new ReviewDTO(
                r.getId(),
                r.getUserId(),
                r.getRole(),
                r.getName(),
                r.getRating(),
                r.getText(),
                r.getCreatedAt() != null ? r.getCreatedAt().toString() : null
        );
    }
}