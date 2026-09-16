package com.bloodlink.controller;

import com.bloodlink.dto.BloodBankAlertDTO;
import com.bloodlink.dto.BloodBankResponseRequest;
import com.bloodlink.security.AuthenticatedUserService;
import com.bloodlink.service.BloodBankRequestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/bloodbanks/{bloodBankId}/alerts")
public class BloodBankAlertController {

    private final BloodBankRequestService bloodBankRequestService;
    private final AuthenticatedUserService authenticatedUserService;

    public BloodBankAlertController(BloodBankRequestService bloodBankRequestService) {
        this(bloodBankRequestService, new AuthenticatedUserService());
    }

    @Autowired
    public BloodBankAlertController(BloodBankRequestService bloodBankRequestService,
                                   AuthenticatedUserService authenticatedUserService) {
        this.bloodBankRequestService = bloodBankRequestService;
        this.authenticatedUserService = authenticatedUserService != null ? authenticatedUserService : new AuthenticatedUserService();
    }

    @GetMapping
    public ResponseEntity<?> getAlerts(@PathVariable("bloodBankId") String bloodBankId) {
        try {
            authenticatedUserService.requireCurrentUser(bloodBankId);
            List<BloodBankAlertDTO> alerts = bloodBankRequestService.getBloodBankAlerts(bloodBankId);
            return ResponseEntity.ok(alerts);
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Collections.singletonMap("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            String msg = e.getMessage();
            if ("BLOOD_BANK_NOT_FOUND".equals(msg)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Collections.singletonMap("error", "Blood Bank not found"));
            } else if ("NOT_A_BLOOD_BANK".equals(msg)) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Collections.singletonMap("error", "User is not a Blood Bank"));
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Collections.singletonMap("error", msg));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Collections.singletonMap("error", "Failed to retrieve blood bank alerts."));
        }
    }

    @PutMapping("/{requestId}")
    public ResponseEntity<?> respondAlert(@PathVariable("bloodBankId") String bloodBankId,
                                         @PathVariable("requestId") String requestId,
                                         @RequestBody BloodBankResponseRequest request) {
        try {
            authenticatedUserService.requireCurrentUser(bloodBankId);
            if (request == null || request.getStatus() == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Collections.singletonMap("error", "Status is required"));
            }
            Map<String, Object> result = bloodBankRequestService.respondToAlert(bloodBankId, requestId, request.getStatus());
            return ResponseEntity.ok(result);
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Collections.singletonMap("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            String msg = e.getMessage();
            if ("BLOOD_BANK_NOT_FOUND".equals(msg)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Collections.singletonMap("error", "Blood Bank not found"));
            } else if ("ALERT_NOT_FOUND".equals(msg)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Collections.singletonMap("error", "Alert request not found for this blood bank"));
            } else if ("REQUEST_NOT_FOUND".equals(msg)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Collections.singletonMap("error", "Blood request not found"));
            } else if ("NOT_A_BLOOD_BANK".equals(msg)) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Collections.singletonMap("error", "User is not a Blood Bank"));
            } else if ("INVALID_STATUS".equals(msg)) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Collections.singletonMap("error", "Invalid status value (must be accepted or rejected)"));
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Collections.singletonMap("error", msg));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Collections.singletonMap("error", "Failed to process alert response. Please try again."));
        }
    }
}
