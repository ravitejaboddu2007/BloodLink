package com.bloodlink.controller;

import com.bloodlink.dto.InventoryDTO;
import com.bloodlink.dto.UpdateInventoryRequest;
import com.bloodlink.security.AuthenticatedUserService;
import com.bloodlink.service.InventoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;

@RestController
@RequestMapping("/api/bloodbanks")
public class BloodBankInventoryController {

    private final InventoryService inventoryService;
    private final AuthenticatedUserService authenticatedUserService;

    public BloodBankInventoryController(InventoryService inventoryService) {
        this(inventoryService, new AuthenticatedUserService());
    }

    @Autowired
    public BloodBankInventoryController(InventoryService inventoryService,
                                        AuthenticatedUserService authenticatedUserService) {
        this.inventoryService = inventoryService;
        this.authenticatedUserService = authenticatedUserService != null ? authenticatedUserService : new AuthenticatedUserService();
    }

    @GetMapping("/{bloodBankId}/inventory")
    public ResponseEntity<?> getInventory(@PathVariable("bloodBankId") String bloodBankId) {
        try {
            InventoryDTO inventory = inventoryService.getInventory(bloodBankId);
            return ResponseEntity.ok(inventory);
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
                    .body(Collections.singletonMap("error", "Failed to retrieve blood bank inventory."));
        }
    }

    @PutMapping("/{bloodBankId}/inventory")
    public ResponseEntity<?> updateInventory(@PathVariable("bloodBankId") String bloodBankId,
                                            @RequestBody UpdateInventoryRequest request) {
        try {
            authenticatedUserService.requireCurrentUser(bloodBankId);
            InventoryDTO updated = inventoryService.updateInventory(bloodBankId, request);
            return ResponseEntity.ok(updated);
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
            } else if ("INVALID_INVENTORY_DATA".equals(msg)) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Collections.singletonMap("error", "Invalid inventory data"));
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Collections.singletonMap("error", msg));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Collections.singletonMap("error", "Failed to update blood bank inventory."));
        }
    }

    @GetMapping("/{bloodBankId}/inventory/{bloodGroup}")
    public ResponseEntity<?> getStockByBloodGroup(@PathVariable("bloodBankId") String bloodBankId,
                                                  @PathVariable("bloodGroup") String bloodGroup) {
        try {
            int stock = inventoryService.getStockByGroup(bloodBankId, bloodGroup);
            return ResponseEntity.ok(Collections.singletonMap("stock", stock));
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
                    .body(Collections.singletonMap("error", "Failed to retrieve blood group stock."));
        }
    }

    @GetMapping("/inventory/updates-count")
    public ResponseEntity<?> getInventoryUpdatesCount() {
        try {
            long count = inventoryService.getInventoryUpdatesCount();
            return ResponseEntity.ok(Collections.singletonMap("count", count));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Collections.singletonMap("error", "Failed to retrieve inventory updates count."));
        }
    }
}
