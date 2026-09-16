package com.bloodlink.controller;

import com.bloodlink.dto.CreateBloodBankMatchesRequest;
import com.bloodlink.dto.CreateBloodRequestRequest;
import com.bloodlink.dto.CreateDonorAlertsRequest;
import com.bloodlink.dto.HospitalRequestDTO;
import com.bloodlink.dto.MatchBloodBanksResponseDTO;
import com.bloodlink.dto.MatchDonorsResponseDTO;
import com.bloodlink.entity.BloodRequest;
import com.bloodlink.entity.RequestBloodBank;
import com.bloodlink.entity.RequestDonor;
import com.bloodlink.repository.BloodRequestRepository;
import com.bloodlink.repository.RequestBloodBankRepository;
import com.bloodlink.repository.RequestDonorRepository;
import com.bloodlink.security.AuthenticatedUserService;
import com.bloodlink.service.BloodBankRequestService;
import com.bloodlink.service.BloodRequestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/requests")
public class HospitalRequestController {

    private final BloodRequestService bloodRequestService;
    private final BloodBankRequestService bloodBankRequestService;
    private final BloodRequestRepository bloodRequestRepository;
    private final RequestDonorRepository requestDonorRepository;
    private final RequestBloodBankRepository requestBloodBankRepository;
    private final AuthenticatedUserService authenticatedUserService;

    public HospitalRequestController(BloodRequestService bloodRequestService,
                                   BloodBankRequestService bloodBankRequestService) {
        this(bloodRequestService, bloodBankRequestService, null, null, null, new AuthenticatedUserService());
    }

    @Autowired
    public HospitalRequestController(BloodRequestService bloodRequestService,
                                   BloodBankRequestService bloodBankRequestService,
                                   BloodRequestRepository bloodRequestRepository,
                                   RequestDonorRepository requestDonorRepository,
                                   RequestBloodBankRepository requestBloodBankRepository,
                                   AuthenticatedUserService authenticatedUserService) {
        this.bloodRequestService = bloodRequestService;
        this.bloodBankRequestService = bloodBankRequestService;
        this.bloodRequestRepository = bloodRequestRepository;
        this.requestDonorRepository = requestDonorRepository;
        this.requestBloodBankRepository = requestBloodBankRepository;
        this.authenticatedUserService = authenticatedUserService != null ? authenticatedUserService : new AuthenticatedUserService();
    }

    private void verifyRequestHospitalOwnership(String requestId, String authHospitalId) {
        if (bloodRequestRepository == null || authHospitalId == null) return;
        BloodRequest r = bloodRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("REQUEST_NOT_FOUND"));
        if (!authHospitalId.equals(r.getHospitalId())) {
            throw new AccessDeniedException("Forbidden: You do not own this blood request");
        }
    }

    private void verifyRequestViewAccess(String requestId, String authUserId) {
        if (bloodRequestRepository == null || authUserId == null) return;
        BloodRequest r = bloodRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("REQUEST_NOT_FOUND"));
        if (authUserId.equals(r.getHospitalId())) {
            return;
        }
        if (requestDonorRepository != null && requestDonorRepository.findByDonorIdAndBloodRequestId(authUserId, requestId).isPresent()) {
            return;
        }
        if (requestBloodBankRepository != null && requestBloodBankRepository.findByBloodBankIdAndBloodRequestId(authUserId, requestId).isPresent()) {
            return;
        }
        throw new AccessDeniedException("Forbidden: You do not have permission to view this request");
    }

    @PostMapping
    public ResponseEntity<?> createRequest(@RequestBody CreateBloodRequestRequest request) {
        try {
            String authHospitalId = authenticatedUserService.getAuthenticatedUserId();
            if (authHospitalId != null) {
                if (request.getHospitalId() != null && !request.getHospitalId().trim().isEmpty()
                        && !authHospitalId.equals(request.getHospitalId().trim())) {
                    throw new AccessDeniedException("Forbidden: Cannot create request for another hospital");
                }
                request.setHospitalId(authHospitalId);
            }
            BloodRequest created = bloodRequestService.createRequest(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Collections.singletonMap("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            String msg = e.getMessage();
            if ("HOSPITAL_NOT_FOUND".equals(msg)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Collections.singletonMap("error", "Hospital not found"));
            } else if ("NOT_A_HOSPITAL".equals(msg)) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Collections.singletonMap("error", "User is not a hospital"));
            } else if ("INVALID_UNITS".equals(msg)) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Collections.singletonMap("error", "Units must be greater than 0"));
            } else if ("INVALID_RADIUS".equals(msg)) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Collections.singletonMap("error", "Radius must be greater than 0"));
            } else if ("MISSING_REQUIRED_FIELDS".equals(msg)) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Collections.singletonMap("error", "Required request fields missing"));
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Collections.singletonMap("error", msg));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Collections.singletonMap("error", "Failed to create blood request. Please try again."));
        }
    }

    @GetMapping("/{requestId}")
    public ResponseEntity<?> getRequestById(@PathVariable("requestId") String requestId) {
        try {
            String authUserId = authenticatedUserService.getAuthenticatedUserId();
            if (authUserId != null) {
                verifyRequestViewAccess(requestId, authUserId);
            }
            HospitalRequestDTO dto = bloodRequestService.getRequestById(requestId);
            return ResponseEntity.ok(dto);
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Collections.singletonMap("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            String msg = e.getMessage();
            if ("REQUEST_NOT_FOUND".equals(msg)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Collections.singletonMap("error", "Blood request not found"));
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Collections.singletonMap("error", msg));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Collections.singletonMap("error", "Failed to retrieve blood request."));
        }
    }

    @GetMapping("/hospital/{hospitalId}")
    public ResponseEntity<?> getHospitalRequests(@PathVariable("hospitalId") String hospitalId) {
        try {
            authenticatedUserService.requireCurrentUser(hospitalId);
            List<HospitalRequestDTO> requests = bloodRequestService.getHospitalRequests(hospitalId);
            return ResponseEntity.ok(requests);
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Collections.singletonMap("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            String msg = e.getMessage();
            if ("HOSPITAL_NOT_FOUND".equals(msg)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Collections.singletonMap("error", "Hospital not found"));
            } else if ("NOT_A_HOSPITAL".equals(msg)) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Collections.singletonMap("error", "User is not a hospital"));
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Collections.singletonMap("error", msg));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Collections.singletonMap("error", "Failed to retrieve hospital requests."));
        }
    }

    @GetMapping("/count")
    public ResponseEntity<?> getTotalRequestCount() {
        try {
            long count = bloodRequestService.getTotalRequestCount();
            return ResponseEntity.ok(Collections.singletonMap("count", count));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Collections.singletonMap("error", "Failed to retrieve request count."));
        }
    }

    @PostMapping("/{requestId}/bloodbanks")
    public ResponseEntity<?> createBloodBankMatches(
            @PathVariable("requestId") String requestId,
            @RequestBody CreateBloodBankMatchesRequest request) {
        try {
            String authHospitalId = authenticatedUserService.getAuthenticatedUserId();
            if (authHospitalId != null) {
                verifyRequestHospitalOwnership(requestId, authHospitalId);
            }
            List<RequestBloodBank> matches = bloodBankRequestService.createBloodBankMatches(requestId, request);
            return ResponseEntity.status(HttpStatus.CREATED).body(matches);
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Collections.singletonMap("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            String msg = e.getMessage();
            if ("REQUEST_NOT_FOUND".equals(msg)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Collections.singletonMap("error", "Blood request not found"));
            } else if ("MISSING_MATCH_DATA".equals(msg)) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Collections.singletonMap("error", "Matched blood bank list is required"));
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Collections.singletonMap("error", msg));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Collections.singletonMap("error", "Failed to save blood bank matches. Please try again."));
        }
    }

    @PostMapping("/{requestId}/match-bloodbanks")
    public ResponseEntity<?> matchBloodBanksForRequest(@PathVariable("requestId") String requestId) {
        try {
            String authHospitalId = authenticatedUserService.getAuthenticatedUserId();
            if (authHospitalId != null) {
                verifyRequestHospitalOwnership(requestId, authHospitalId);
            }
            MatchBloodBanksResponseDTO response = bloodBankRequestService.matchBloodBanksForRequest(requestId);
            return ResponseEntity.ok(response);
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Collections.singletonMap("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            String msg = e.getMessage();
            if ("REQUEST_NOT_FOUND".equals(msg)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Collections.singletonMap("error", "Blood request not found"));
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Collections.singletonMap("error", msg));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Collections.singletonMap("error", "Failed to match blood banks. Please try again."));
        }
    }

    @PostMapping("/{requestId}/match-donors")
    public ResponseEntity<?> matchDonorsForRequest(@PathVariable("requestId") String requestId) {
        try {
            String authHospitalId = authenticatedUserService.getAuthenticatedUserId();
            if (authHospitalId != null) {
                verifyRequestHospitalOwnership(requestId, authHospitalId);
            }
            MatchDonorsResponseDTO response = bloodRequestService.matchDonorsForRequest(requestId);
            return ResponseEntity.ok(response);
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Collections.singletonMap("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            String msg = e.getMessage();
            if ("REQUEST_NOT_FOUND".equals(msg)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Collections.singletonMap("error", "Blood request not found"));
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Collections.singletonMap("error", msg));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Collections.singletonMap("error", "Failed to match donors. Please try again."));
        }
    }

    @PostMapping("/{requestId}/increase-radius")
    public ResponseEntity<?> increaseRequestRadius(@PathVariable("requestId") String requestId) {
        try {
            String authHospitalId = authenticatedUserService.getAuthenticatedUserId();
            if (authHospitalId != null) {
                verifyRequestHospitalOwnership(requestId, authHospitalId);
            }
            Map<String, Object> response = bloodRequestService.increaseRequestRadius(requestId);
            return ResponseEntity.ok(response);
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Collections.singletonMap("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            String msg = e.getMessage();
            if ("REQUEST_NOT_FOUND".equals(msg)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Collections.singletonMap("error", "Blood request not found"));
            } else if ("MAX_RADIUS_REACHED".equals(msg)) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Collections.singletonMap("error", "Maximum radius (50km) reached"));
            } else if ("REQUEST_INACTIVE".equals(msg)) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Collections.singletonMap("error", "Cannot increase radius for an inactive request"));
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Collections.singletonMap("error", msg));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Collections.singletonMap("error", "Failed to increase search radius. Please try again."));
        }
    }

    @PostMapping("/{requestId}/alerts")
    public ResponseEntity<?> createDonorAlerts(
            @PathVariable("requestId") String requestId,
            @RequestBody CreateDonorAlertsRequest request) {
        try {
            String authHospitalId = authenticatedUserService.getAuthenticatedUserId();
            if (authHospitalId != null) {
                verifyRequestHospitalOwnership(requestId, authHospitalId);
            }
            List<RequestDonor> alerts = bloodRequestService.createDonorAlerts(requestId, request);
            return ResponseEntity.status(HttpStatus.CREATED).body(alerts);
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Collections.singletonMap("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            String msg = e.getMessage();
            if ("REQUEST_NOT_FOUND".equals(msg)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Collections.singletonMap("error", "Blood request not found"));
            } else if ("MISSING_ALERT_DATA".equals(msg)) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Collections.singletonMap("error", "Matched donor list is required"));
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Collections.singletonMap("error", msg));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Collections.singletonMap("error", "Failed to create donor alerts. Please try again."));
        }
    }

    @PostMapping("/{requestId}/progress-wave")
    public ResponseEntity<?> progressWave(@PathVariable("requestId") String requestId) {
        try {
            String authHospitalId = authenticatedUserService.getAuthenticatedUserId();
            if (authHospitalId != null) {
                verifyRequestHospitalOwnership(requestId, authHospitalId);
            }
            List<RequestDonor> nextWave = bloodRequestService.progressDonorAlertWavesForRequest(requestId);
            return ResponseEntity.ok(nextWave);
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Collections.singletonMap("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Collections.singletonMap("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Collections.singletonMap("error", "Failed to progress wave"));
        }
    }

    @PutMapping("/{requestId}/fulfill")
    public ResponseEntity<?> fulfillRequest(
            @PathVariable("requestId") String requestId,
            @RequestBody(required = false) java.util.Map<String, String> body) {
        try {
            String authHospitalId = authenticatedUserService.getAuthenticatedUserId();
            if (authHospitalId != null) {
                verifyRequestHospitalOwnership(requestId, authHospitalId);
            } else {
                authHospitalId = body != null ? body.get("hospitalId") : null;
            }
            BloodRequest fulfilled = bloodRequestService.fulfillRequest(requestId, authHospitalId);
            return ResponseEntity.ok(fulfilled);
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Collections.singletonMap("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            String msg = e.getMessage();
            if ("REQUEST_NOT_FOUND".equals(msg)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Collections.singletonMap("error", "Blood request not found"));
            } else if ("UNAUTHORIZED".equals(msg)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Collections.singletonMap("error", "Unauthorized to fulfill this request"));
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Collections.singletonMap("error", msg));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Collections.singletonMap("error", "Failed to fulfill blood request. Please try again."));
        }
    }

    @PutMapping("/{requestId}/donors/{donorId}/confirm")
    public ResponseEntity<?> confirmDonor(
            @PathVariable("requestId") String requestId,
            @PathVariable("donorId") String donorId,
            @RequestBody(required = false) java.util.Map<String, String> body) {
        try {
            String authHospitalId = authenticatedUserService.getAuthenticatedUserId();
            if (authHospitalId != null) {
                verifyRequestHospitalOwnership(requestId, authHospitalId);
            } else {
                authHospitalId = body != null ? body.get("hospitalId") : null;
            }
            BloodRequest updated = bloodRequestService.confirmDonor(requestId, donorId, authHospitalId);
            return ResponseEntity.ok(updated);
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Collections.singletonMap("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            String msg = e.getMessage();
            if ("REQUEST_NOT_FOUND".equals(msg)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Collections.singletonMap("error", "Blood request not found"));
            } else if ("ALERT_NOT_FOUND".equals(msg) || "DONOR_NOT_FOUND".equals(msg)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Collections.singletonMap("error", "Donor alert not found"));
            } else if ("UNAUTHORIZED".equals(msg)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Collections.singletonMap("error", "Unauthorized to confirm donor for this request"));
            } else if ("ACCUMULATION_DEADLINE_PASSED".equals(msg)) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Collections.singletonMap("error", "Accumulation deadline has passed"));
            } else if ("REQUEST_ALREADY_FULFILLED".equals(msg)) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Collections.singletonMap("error", "Request is already fulfilled"));
            } else if ("DONOR_NOT_ACCEPTED".equals(msg)) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Collections.singletonMap("error", "Donor has not accepted this request yet"));
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Collections.singletonMap("error", msg));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Collections.singletonMap("error", "Failed to confirm donor. Please try again."));
        }
    }

    @PutMapping("/{requestId}/bloodbanks/{bloodBankId}/confirm")
    public ResponseEntity<?> confirmBloodBank(
            @PathVariable("requestId") String requestId,
            @PathVariable("bloodBankId") String bloodBankId,
            @RequestBody(required = false) java.util.Map<String, String> body) {
        try {
            String authHospitalId = authenticatedUserService.getAuthenticatedUserId();
            if (authHospitalId != null) {
                verifyRequestHospitalOwnership(requestId, authHospitalId);
            } else {
                authHospitalId = body != null ? body.get("hospitalId") : null;
            }
            BloodRequest updated = bloodBankRequestService.confirmBloodBank(requestId, bloodBankId, authHospitalId);
            return ResponseEntity.ok(updated);
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Collections.singletonMap("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            String msg = e.getMessage();
            if ("REQUEST_NOT_FOUND".equals(msg)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Collections.singletonMap("error", "Blood request not found"));
            } else if ("BLOOD_BANK_NOT_FOUND".equals(msg) || "ALERT_NOT_FOUND".equals(msg)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Collections.singletonMap("error", "Blood Bank alert not found"));
            } else if ("NOT_A_BLOOD_BANK".equals(msg)) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Collections.singletonMap("error", "User is not a blood bank"));
            } else if ("UNAUTHORIZED".equals(msg)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Collections.singletonMap("error", "Unauthorized to confirm blood bank for this request"));
            } else if ("ACCUMULATION_DEADLINE_PASSED".equals(msg)) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Collections.singletonMap("error", "Accumulation deadline has passed"));
            } else if ("REQUEST_ALREADY_FULFILLED".equals(msg)) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Collections.singletonMap("error", "Request is already fulfilled"));
            } else if ("BLOOD_BANK_NOT_ACCEPTED".equals(msg)) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Collections.singletonMap("error", "Blood Bank has not accepted this request yet"));
            } else if ("ALREADY_REJECTED".equals(msg)) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Collections.singletonMap("error", "Blood Bank response has already been rejected"));
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Collections.singletonMap("error", msg));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Collections.singletonMap("error", "Failed to confirm blood bank. Please try again."));
        }
    }

    @PutMapping("/{requestId}/bloodbanks/{bloodBankId}/reject")
    public ResponseEntity<?> rejectBloodBank(
            @PathVariable("requestId") String requestId,
            @PathVariable("bloodBankId") String bloodBankId,
            @RequestBody(required = false) java.util.Map<String, String> body) {
        try {
            String authHospitalId = authenticatedUserService.getAuthenticatedUserId();
            if (authHospitalId != null) {
                verifyRequestHospitalOwnership(requestId, authHospitalId);
            } else {
                authHospitalId = body != null ? body.get("hospitalId") : null;
            }
            BloodRequest updated = bloodBankRequestService.rejectBloodBank(requestId, bloodBankId, authHospitalId);
            return ResponseEntity.ok(updated);
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Collections.singletonMap("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            String msg = e.getMessage();
            if ("REQUEST_NOT_FOUND".equals(msg)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Collections.singletonMap("error", "Blood request not found"));
            } else if ("BLOOD_BANK_NOT_FOUND".equals(msg) || "ALERT_NOT_FOUND".equals(msg)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Collections.singletonMap("error", "Blood Bank alert not found"));
            } else if ("NOT_A_BLOOD_BANK".equals(msg)) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Collections.singletonMap("error", "User is not a blood bank"));
            } else if ("UNAUTHORIZED".equals(msg)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Collections.singletonMap("error", "Unauthorized to reject blood bank for this request"));
            } else if ("BLOOD_BANK_NOT_ACCEPTED".equals(msg)) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Collections.singletonMap("error", "Blood Bank has not accepted this request yet"));
            } else if ("ALREADY_CONFIRMED".equals(msg)) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Collections.singletonMap("error", "Blood Bank response has already been confirmed"));
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Collections.singletonMap("error", msg));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Collections.singletonMap("error", "Failed to reject blood bank. Please try again."));
        }
    }
}