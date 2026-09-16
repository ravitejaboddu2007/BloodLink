package com.bloodlink.controller;

import com.bloodlink.dto.HospitalRequestDTO;
import com.bloodlink.security.AuthenticatedUserService;
import com.bloodlink.service.BloodRequestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/api/hospitals")
public class HospitalController {

    private final BloodRequestService bloodRequestService;
    private final AuthenticatedUserService authenticatedUserService;

    public HospitalController(BloodRequestService bloodRequestService) {
        this(bloodRequestService, new AuthenticatedUserService());
    }

    @Autowired
    public HospitalController(BloodRequestService bloodRequestService, AuthenticatedUserService authenticatedUserService) {
        this.bloodRequestService = bloodRequestService;
        this.authenticatedUserService = authenticatedUserService != null ? authenticatedUserService : new AuthenticatedUserService();
    }

    @GetMapping("/{hospitalId}/requests")
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
                    .body(Collections.singletonMap("error", "Failed to retrieve hospital requests. Please try again."));
        }
    }
}
