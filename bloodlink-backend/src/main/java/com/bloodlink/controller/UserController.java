package com.bloodlink.controller;

import com.bloodlink.dto.UserProfileDTO;
import com.bloodlink.security.AuthenticatedUserService;
import com.bloodlink.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final AuthenticatedUserService authenticatedUserService;

    public UserController(UserService userService) {
        this(userService, new AuthenticatedUserService());
    }

    @Autowired
    public UserController(UserService userService, AuthenticatedUserService authenticatedUserService) {
        this.userService = userService;
        this.authenticatedUserService = authenticatedUserService != null ? authenticatedUserService : new AuthenticatedUserService();
    }

    @GetMapping("/stats")
    public ResponseEntity<?> getUserStats() {
        try {
            java.util.Map<String, Long> stats = userService.getUserStats();
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Collections.singletonMap("error", "Failed to retrieve user stats."));
        }
    }

    @GetMapping("/{userId}")
    public ResponseEntity<?> getUserProfile(@PathVariable("userId") String userId) {
        try {
            authenticatedUserService.requireCurrentUser(userId);
            UserProfileDTO profile = userService.getUserProfile(userId);
            return ResponseEntity.ok(profile);
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Collections.singletonMap("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            String msg = e.getMessage();
            if ("USER_NOT_FOUND".equals(msg)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Collections.singletonMap("error", "User not found"));
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Collections.singletonMap("error", msg));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Collections.singletonMap("error", "Failed to retrieve user profile. Please try again."));
        }
    }

    @GetMapping("/{userId}/exists")
    public ResponseEntity<?> checkUserExists(@PathVariable("userId") String userId) {
        try {
            boolean exists = userService.existsUser(userId);
            return ResponseEntity.ok(Collections.singletonMap("exists", exists));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Collections.singletonMap("error", "Failed to check user existence."));
        }
    }

    @PutMapping("/{userId}")
    public ResponseEntity<?> updateUserProfile(@PathVariable("userId") String userId,
                                              @RequestBody com.bloodlink.dto.UpdateProfileRequest request) {
        try {
            authenticatedUserService.requireCurrentUser(userId);
            UserProfileDTO updated = userService.updateUserProfile(userId, request);
            return ResponseEntity.ok(updated);
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Collections.singletonMap("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            String msg = e.getMessage();
            if ("USER_NOT_FOUND".equals(msg)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Collections.singletonMap("error", "User not found"));
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Collections.singletonMap("error", msg));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Collections.singletonMap("error", "Failed to update user profile. Please try again."));
        }
    }
}