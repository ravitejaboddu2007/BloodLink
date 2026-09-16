package com.bloodlink.controller;

import com.bloodlink.dto.AlertResponseRequest;
import com.bloodlink.dto.AvailabilityRequest;
import com.bloodlink.dto.CreateDonationRequest;
import com.bloodlink.dto.DonationHistoryDTO;
import com.bloodlink.dto.DonorAlertDTO;
import com.bloodlink.dto.DonorSearchItemDTO;
import com.bloodlink.dto.LocationUpdateRequest;
import com.bloodlink.security.AuthenticatedUserService;
import com.bloodlink.service.DonationHistoryService;
import com.bloodlink.service.DonorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/donors")
public class DonorController {

    private final DonorService donorService;
    private final DonationHistoryService donationHistoryService;
    private final AuthenticatedUserService authenticatedUserService;

    public DonorController(DonorService donorService) {
        this(donorService, null, new AuthenticatedUserService());
    }

    public DonorController(DonorService donorService, DonationHistoryService donationHistoryService) {
        this(donorService, donationHistoryService, new AuthenticatedUserService());
    }

    @Autowired
    public DonorController(DonorService donorService, DonationHistoryService donationHistoryService,
                           AuthenticatedUserService authenticatedUserService) {
        this.donorService = donorService;
        this.donationHistoryService = donationHistoryService;
        this.authenticatedUserService = authenticatedUserService != null ? authenticatedUserService : new AuthenticatedUserService();
    }

    @GetMapping("/count/available")
    public ResponseEntity<?> getAvailableDonorCount() {
        try {
            long count = donorService.getAvailableDonorCount();
            return ResponseEntity.ok(Collections.singletonMap("count", count));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Collections.singletonMap("error", "Failed to retrieve available donor count."));
        }
    }

    @GetMapping("/search")
    public ResponseEntity<?> searchDonors(
            @RequestParam(value = "bloodGroup", required = false) String bloodGroup,
            @RequestParam(value = "available", required = false) Boolean available,
            @RequestParam(value = "city", required = false) String city,
            @RequestParam(value = "lat", required = false) Double lat,
            @RequestParam(value = "lng", required = false) Double lng,
            @RequestParam(value = "radius", required = false) Integer radius) {
        try {
            List<DonorSearchItemDTO> results = donorService.searchDonors(bloodGroup, available, city, lat, lng, radius);
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Collections.singletonMap("error", "Failed to search donors."));
        }
    }

    @GetMapping("/{donorId}/history")
    public ResponseEntity<?> getDonationHistory(@PathVariable("donorId") String donorId) {
        try {
            authenticatedUserService.requireCurrentUser(donorId);
            if (donationHistoryService == null) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Collections.singletonMap("error", "DonationHistoryService not configured."));
            }
            List<DonationHistoryDTO> history = donationHistoryService.getDonationHistory(donorId);
            return ResponseEntity.ok(history);
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Collections.singletonMap("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            String msg = e.getMessage();
            if ("DONOR_NOT_FOUND".equals(msg)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Collections.singletonMap("error", "Donor not found"));
            } else if ("NOT_A_DONOR".equals(msg)) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Collections.singletonMap("error", "User is not a donor"));
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Collections.singletonMap("error", msg));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Collections.singletonMap("error", "Failed to retrieve donation history."));
        }
    }

    @PostMapping("/{donorId}/history")
    public ResponseEntity<?> createDonation(
            @PathVariable("donorId") String donorId,
            @RequestBody CreateDonationRequest request) {
        try {
            authenticatedUserService.requireCurrentUser(donorId);
            if (donationHistoryService == null) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Collections.singletonMap("error", "DonationHistoryService not configured."));
            }
            DonationHistoryDTO created = donationHistoryService.createDonation(donorId, request);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Collections.singletonMap("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            String msg = e.getMessage();
            if ("DONOR_NOT_FOUND".equals(msg)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Collections.singletonMap("error", "Donor not found"));
            } else if ("NOT_A_DONOR".equals(msg)) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Collections.singletonMap("error", "User is not a donor"));
            } else if ("MISSING_DATA".equals(msg) || "MISSING_DATE".equals(msg) || "MISSING_LOCATION".equals(msg)) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Collections.singletonMap("error", msg));
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Collections.singletonMap("error", msg));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Collections.singletonMap("error", "Failed to create donation history."));
        }
    }

    @PutMapping("/{id}/availability")
    public ResponseEntity<?> updateAvailability(
            @PathVariable("id") String donorId,
            @RequestBody AvailabilityRequest request) {
        try {
            authenticatedUserService.requireCurrentUser(donorId);
            Boolean available = request != null ? request.getAvailable() : null;
            Map<String, Object> response = donorService.updateAvailability(donorId, available);
            return ResponseEntity.ok(response);
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Collections.singletonMap("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            String msg = e.getMessage();
            if ("DONOR_NOT_FOUND".equals(msg)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Collections.singletonMap("error", "Donor not found"));
            } else if ("NOT_A_DONOR".equals(msg)) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Collections.singletonMap("error", "User is not a donor"));
            } else if ("MISSING_DATA".equals(msg)) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Collections.singletonMap("error", "Availability value is required"));
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Collections.singletonMap("error", msg));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Collections.singletonMap("error", "Failed to update availability. Please try again."));
        }
    }

    @PutMapping("/{id}/location")
    public ResponseEntity<?> updateLocation(
            @PathVariable("id") String donorId,
            @RequestBody LocationUpdateRequest request) {
        try {
            authenticatedUserService.requireCurrentUser(donorId);
            Map<String, Object> response = donorService.updateLocation(donorId, request);
            return ResponseEntity.ok(response);
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Collections.singletonMap("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            String msg = e.getMessage();
            if ("DONOR_NOT_FOUND".equals(msg)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Collections.singletonMap("error", "Donor not found"));
            } else if ("NOT_A_DONOR".equals(msg)) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Collections.singletonMap("error", "User is not a donor"));
            } else if ("INVALID_COORDINATES".equals(msg)) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Collections.singletonMap("error", "Valid latitude and longitude are required"));
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Collections.singletonMap("error", msg));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Collections.singletonMap("error", "Failed to update location. Please try again."));
        }
    }

    @GetMapping("/{donorId}/alerts")
    public ResponseEntity<?> getDonorAlerts(@PathVariable("donorId") String donorId) {
        try {
            authenticatedUserService.requireCurrentUser(donorId);
            List<DonorAlertDTO> alerts = donorService.getDonorAlerts(donorId);
            return ResponseEntity.ok(alerts);
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Collections.singletonMap("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            String msg = e.getMessage();
            if ("DONOR_NOT_FOUND".equals(msg)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Collections.singletonMap("error", "Donor not found"));
            } else if ("NOT_A_DONOR".equals(msg)) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Collections.singletonMap("error", "User is not a donor"));
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Collections.singletonMap("error", msg));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Collections.singletonMap("error", "Failed to retrieve donor alerts. Please try again."));
        }
    }

    @PutMapping("/{donorId}/alerts/{requestId}")
    public ResponseEntity<?> respondToAlert(
            @PathVariable("donorId") String donorId,
            @PathVariable("requestId") String requestId,
            @RequestBody AlertResponseRequest request) {
        try {
            authenticatedUserService.requireCurrentUser(donorId);
            Map<String, Object> response = donorService.respondToAlert(donorId, requestId, request);
            return ResponseEntity.ok(response);
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Collections.singletonMap("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            String msg = e.getMessage();
            if ("DONOR_NOT_FOUND".equals(msg)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Collections.singletonMap("error", "Donor not found"));
            } else if ("NOT_A_DONOR".equals(msg)) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Collections.singletonMap("error", "User is not a donor"));
            } else if ("ALERT_NOT_FOUND".equals(msg)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Collections.singletonMap("error", "Blood request alert not found for this donor"));
            } else if ("INVALID_STATUS".equals(msg)) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Collections.singletonMap("error", "Valid status ('accepted' or 'rejected') is required"));
            } else if ("INVALID_COORDINATES".equals(msg)) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Collections.singletonMap("error", "Valid latitude and longitude coordinates are required"));
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Collections.singletonMap("error", msg));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Collections.singletonMap("error", "Failed to update alert response. Please try again."));
        }
    }
}