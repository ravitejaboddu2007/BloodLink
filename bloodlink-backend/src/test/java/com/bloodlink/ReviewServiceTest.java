package com.bloodlink;

import com.bloodlink.controller.ReviewController;
import com.bloodlink.dto.ReviewDTO;
import com.bloodlink.dto.ReviewRequest;
import com.bloodlink.entity.Review;
import com.bloodlink.entity.User;
import com.bloodlink.repository.ReviewRepository;
import com.bloodlink.repository.UserRepository;
import com.bloodlink.service.ReviewService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class ReviewServiceTest {

    private ReviewRepository reviewRepository;
    private UserRepository userRepository;
    private ReviewService reviewService;
    private ReviewController reviewController;

    @BeforeEach
    public void setUp() {
        reviewRepository = Mockito.mock(ReviewRepository.class);
        userRepository = Mockito.mock(UserRepository.class);

        reviewService = new ReviewService(reviewRepository, userRepository);
        reviewController = new ReviewController(reviewService);
    }

    private User createUser(String id, String role, String name) {
        User u = new User();
        u.setId(id);
        u.setRole(role);
        u.setName(name);
        u.setEmail(id + "@example.com");
        u.setPasswordHash("$2a$10$hashedpw");
        return u;
    }

    private Review createReview(String id, String userId, String role, String name, int rating, String text, LocalDateTime createdAt) {
        Review r = new Review();
        r.setId(id);
        r.setUserId(userId);
        r.setRole(role);
        r.setName(name);
        r.setRating(rating);
        r.setText(text);
        r.setCreatedAt(createdAt);
        return r;
    }

    @Test
    public void testGetAllReviewsSuccess() {
        Review r1 = createReview("rev1", "u1", "donor", "John", 5, "Great platform!", LocalDateTime.of(2026, 2, 1, 10, 0));
        Review r2 = createReview("rev2", "u2", "hospital", "City Hospital", 4, "Very fast matching.", LocalDateTime.of(2026, 1, 15, 12, 0));
        when(reviewRepository.findAllByOrderByCreatedAtDesc()).thenReturn(Arrays.asList(r1, r2));

        List<ReviewDTO> result = reviewService.getAllReviews();

        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("rev1", result.get(0).getId());
        assertEquals("u1", result.get(0).getUserId());
        assertEquals("donor", result.get(0).getRole());
        assertEquals("John", result.get(0).getName());
        assertEquals(5, result.get(0).getRating());
        assertEquals("Great platform!", result.get(0).getText());
        assertNotNull(result.get(0).getCreatedAt());
    }

    @Test
    public void testGetAllReviewsEmptyReturnsEmptyList() {
        when(reviewRepository.findAllByOrderByCreatedAtDesc()).thenReturn(new ArrayList<>());

        List<ReviewDTO> result = reviewService.getAllReviews();
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testGetReviewByUserIdSuccess() {
        Review r = createReview("rev1", "d1", "donor", "Alice", 5, "Saved a life!", LocalDateTime.now());
        when(reviewRepository.findByUserId("d1")).thenReturn(Optional.of(r));

        Optional<ReviewDTO> opt = reviewService.getReviewByUserId("d1");
        assertTrue(opt.isPresent());
        assertEquals("rev1", opt.get().getId());
        assertEquals("d1", opt.get().getUserId());
        assertEquals("Alice", opt.get().getName());
    }

    @Test
    public void testGetReviewByUserIdNotFound() {
        when(reviewRepository.findByUserId("unknown")).thenReturn(Optional.empty());

        Optional<ReviewDTO> opt = reviewService.getReviewByUserId("unknown");
        assertFalse(opt.isPresent());
    }

    @Test
    public void testCreateNewReviewSuccess() {
        User donor = createUser("d1", "donor", "Alice Smith");
        when(userRepository.findById("d1")).thenReturn(Optional.of(donor));
        when(reviewRepository.findByUserId("d1")).thenReturn(Optional.empty());
        when(reviewRepository.save(any(Review.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ReviewRequest req = new ReviewRequest("d1", 5, "Wonderful service!");
        ReviewDTO created = reviewService.createOrUpdateReview("d1", req);

        assertNotNull(created);
        assertEquals("d1", created.getUserId());
        assertEquals("donor", created.getRole());
        assertEquals("Alice Smith", created.getName());
        assertEquals(5, created.getRating());
        assertEquals("Wonderful service!", created.getText());
        assertNotNull(created.getId());
        assertNotNull(created.getCreatedAt());

        verify(reviewRepository, times(1)).save(any(Review.class));
    }

    @Test
    public void testUpdateExistingReviewSuccess() {
        User hospital = createUser("h1", "hospital", "Metro Hospital");
        when(userRepository.findById("h1")).thenReturn(Optional.of(hospital));

        Review existing = createReview("rev_old", "h1", "hospital", "Metro Hospital", 4, "Initial review", LocalDateTime.of(2025, 12, 1, 10, 0));
        when(reviewRepository.findByUserId("h1")).thenReturn(Optional.of(existing));
        when(reviewRepository.save(any(Review.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ReviewRequest req = new ReviewRequest("h1", 5, "Updated review text: even better!");
        ReviewDTO updated = reviewService.createOrUpdateReview("h1", req);

        assertNotNull(updated);
        assertEquals("rev_old", updated.getId()); // Preserves ID
        assertEquals("h1", updated.getUserId());
        assertEquals(5, updated.getRating());
        assertEquals("Updated review text: even better!", updated.getText());

        verify(reviewRepository, times(1)).save(existing);
    }

    @Test
    public void testCreateReviewRejectsInvalidRating() {
        User user = createUser("u1", "donor", "Bob");
        when(userRepository.findById("u1")).thenReturn(Optional.of(user));

        // Rating < 1
        assertThrows(IllegalArgumentException.class, () -> {
            reviewService.createOrUpdateReview("u1", new ReviewRequest("u1", 0, "Text"));
        });

        // Rating > 5
        assertThrows(IllegalArgumentException.class, () -> {
            reviewService.createOrUpdateReview("u1", new ReviewRequest("u1", 6, "Text"));
        });
    }

    @Test
    public void testCreateReviewRejectsMissingText() {
        User user = createUser("u1", "donor", "Bob");
        when(userRepository.findById("u1")).thenReturn(Optional.of(user));

        assertThrows(IllegalArgumentException.class, () -> {
            reviewService.createOrUpdateReview("u1", new ReviewRequest("u1", 5, null));
        });

        assertThrows(IllegalArgumentException.class, () -> {
            reviewService.createOrUpdateReview("u1", new ReviewRequest("u1", 5, "   "));
        });
    }

    @Test
    public void testCreateReviewRejectsUnknownUser() {
        when(userRepository.findById("unknown")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> {
            reviewService.createOrUpdateReview("unknown", new ReviewRequest("unknown", 5, "Text"));
        });
    }

    @Test
    public void testReviewDTODoesNotExposePassword() {
        for (Field f : ReviewDTO.class.getDeclaredFields()) {
            String name = f.getName().toLowerCase();
            assertFalse(name.contains("password"), "ReviewDTO must not contain password field: " + f.getName());
            assertFalse(name.contains("passwordhash"), "ReviewDTO must not contain passwordHash field: " + f.getName());
        }
        for (Method m : ReviewDTO.class.getDeclaredMethods()) {
            String name = m.getName().toLowerCase();
            assertFalse(name.contains("password"), "ReviewDTO must not contain password method: " + m.getName());
            assertFalse(name.contains("passwordhash"), "ReviewDTO must not contain passwordHash method: " + m.getName());
        }
    }

    @Test
    public void testReviewControllerEndpoints() {
        Review r = createReview("rev1", "d1", "donor", "Alice", 5, "Nice!", LocalDateTime.now());
        when(reviewRepository.findAllByOrderByCreatedAtDesc()).thenReturn(Arrays.asList(r));
        when(reviewRepository.findByUserId("d1")).thenReturn(Optional.of(r));

        User user = createUser("d1", "donor", "Alice");
        when(userRepository.findById("d1")).thenReturn(Optional.of(user));
        when(reviewRepository.save(any(Review.class))).thenReturn(r);

        // GET all
        ResponseEntity<?> allResp = reviewController.getAllReviews();
        assertEquals(HttpStatus.OK, allResp.getStatusCode());
        assertTrue(allResp.getBody() instanceof List);

        // GET by user
        ResponseEntity<?> userResp = reviewController.getReviewByUser("d1");
        assertEquals(HttpStatus.OK, userResp.getStatusCode());
        assertTrue(userResp.getBody() instanceof ReviewDTO);

        // GET by non-existent user
        when(reviewRepository.findByUserId("missing")).thenReturn(Optional.empty());
        ResponseEntity<?> missingResp = reviewController.getReviewByUser("missing");
        assertEquals(HttpStatus.NOT_FOUND, missingResp.getStatusCode());

        // POST submit review
        ReviewRequest req = new ReviewRequest("d1", 5, "Nice!");
        ResponseEntity<?> postResp = reviewController.submitReview(req);
        assertEquals(HttpStatus.OK, postResp.getStatusCode());
        assertTrue(postResp.getBody() instanceof ReviewDTO);

        // POST submit review with missing userId
        ResponseEntity<?> badPost = reviewController.submitReview(new ReviewRequest(null, 5, "Nice!"));
        assertEquals(HttpStatus.BAD_REQUEST, badPost.getStatusCode());

        // DELETE by id success
        when(reviewRepository.findById("rev1")).thenReturn(Optional.of(r));
        ResponseEntity<?> delResp = reviewController.deleteReview("rev1", "d1");
        assertEquals(HttpStatus.OK, delResp.getStatusCode());

        // DELETE by id unauthorized
        ResponseEntity<?> unauthDel = reviewController.deleteReview("rev1", "other_user");
        assertEquals(HttpStatus.FORBIDDEN, unauthDel.getStatusCode());

        // DELETE by user success
        ResponseEntity<?> delUserResp = reviewController.deleteReviewByUser("d1");
        assertEquals(HttpStatus.OK, delUserResp.getStatusCode());
    }

    @Test
    public void testDeleteReviewService() {
        Review r = createReview("rev1", "d1", "donor", "Alice", 5, "Nice!", LocalDateTime.now());
        when(reviewRepository.findById("rev1")).thenReturn(Optional.of(r));
        when(reviewRepository.findByUserId("d1")).thenReturn(Optional.of(r));

        // Delete success by id
        reviewService.deleteReview("rev1", "d1");
        verify(reviewRepository, times(1)).delete(r);

        // Delete unauthorized
        assertThrows(IllegalArgumentException.class, () -> {
            reviewService.deleteReview("rev1", "wrong_user");
        });

        // Delete not found
        when(reviewRepository.findById("missing")).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> {
            reviewService.deleteReview("missing", "d1");
        });

        // Delete by user id success
        reviewService.deleteReviewByUserId("d1");
        verify(reviewRepository, times(2)).delete(r);
    }
}