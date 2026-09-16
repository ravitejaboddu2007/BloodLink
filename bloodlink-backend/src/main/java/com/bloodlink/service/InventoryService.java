package com.bloodlink.service;

import com.bloodlink.dto.InventoryDTO;
import com.bloodlink.dto.UpdateInventoryRequest;
import com.bloodlink.entity.Inventory;
import com.bloodlink.entity.RequestBloodBank;
import com.bloodlink.entity.User;
import com.bloodlink.repository.InventoryRepository;
import com.bloodlink.repository.RequestBloodBankRepository;
import com.bloodlink.repository.UserRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class InventoryService {

    public static final Set<String> VALID_BLOOD_GROUPS = new HashSet<>(Arrays.asList(
            "A+", "A-", "B+", "B-", "O+", "O-", "AB+", "AB-"
    ));

    private final InventoryRepository inventoryRepository;
    private final UserRepository userRepository;
    private final RequestBloodBankRepository requestBloodBankRepository;
    private final ObjectMapper objectMapper;

    public InventoryService(InventoryRepository inventoryRepository,
                            UserRepository userRepository,
                            ObjectMapper objectMapper) {
        this.inventoryRepository = inventoryRepository;
        this.userRepository = userRepository;
        this.requestBloodBankRepository = null;
        this.objectMapper = objectMapper;
    }

    @Autowired
    public InventoryService(InventoryRepository inventoryRepository,
                            UserRepository userRepository,
                            RequestBloodBankRepository requestBloodBankRepository,
                            ObjectMapper objectMapper) {
        this.inventoryRepository = inventoryRepository;
        this.userRepository = userRepository;
        this.requestBloodBankRepository = requestBloodBankRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public InventoryDTO getInventory(String bloodBankId) {
        if (bloodBankId == null || bloodBankId.trim().isEmpty()) {
            throw new IllegalArgumentException("BLOOD_BANK_NOT_FOUND");
        }

        User user = userRepository.findById(bloodBankId.trim())
                .orElseThrow(() -> new IllegalArgumentException("BLOOD_BANK_NOT_FOUND"));

        if (!"bloodbank".equalsIgnoreCase(user.getRole())) {
            throw new IllegalArgumentException("NOT_A_BLOOD_BANK");
        }

        Inventory inventory = inventoryRepository.findByBloodBankId(bloodBankId.trim())
                .orElseGet(() -> {
                    Inventory newInv = new Inventory();
                    newInv.setBloodBankId(bloodBankId.trim());
                    newInv.setStockAPos(0);
                    newInv.setStockANeg(0);
                    newInv.setStockBPos(0);
                    newInv.setStockBNeg(0);
                    newInv.setStockOPos(0);
                    newInv.setStockONeg(0);
                    newInv.setStockAbPos(0);
                    newInv.setStockAbNeg(0);
                    newInv.setLastUpdatedJson("{}");
                    return inventoryRepository.save(newInv);
                });

        return mapToDTO(inventory);
    }

    @Transactional
    public InventoryDTO updateInventory(String bloodBankId, UpdateInventoryRequest request) {
        if (bloodBankId == null || bloodBankId.trim().isEmpty()) {
            throw new IllegalArgumentException("BLOOD_BANK_NOT_FOUND");
        }

        User user = userRepository.findById(bloodBankId.trim())
                .orElseThrow(() -> new IllegalArgumentException("BLOOD_BANK_NOT_FOUND"));

        if (!"bloodbank".equalsIgnoreCase(user.getRole())) {
            throw new IllegalArgumentException("NOT_A_BLOOD_BANK");
        }

        if (request == null || request.getStock() == null) {
            throw new IllegalArgumentException("INVALID_INVENTORY_DATA");
        }

        Map<String, Integer> stockMap = request.getStock();

        // Validate blood groups and non-negative integers
        for (Map.Entry<String, Integer> entry : stockMap.entrySet()) {
            String bg = entry.getKey();
            Integer qty = entry.getValue();

            if (!VALID_BLOOD_GROUPS.contains(bg)) {
                throw new IllegalArgumentException("INVALID_BLOOD_GROUP: " + bg);
            }
            if (qty == null || qty < 0) {
                throw new IllegalArgumentException("INVALID_QUANTITY: Units cannot be negative");
            }
        }

        Inventory inventory = inventoryRepository.findByBloodBankId(bloodBankId.trim())
                .orElseGet(() -> {
                    Inventory newInv = new Inventory();
                    newInv.setBloodBankId(bloodBankId.trim());
                    return newInv;
                });

        Map<String, String> lastUpdatedMap = parseLastUpdated(inventory.getLastUpdatedJson());
        String nowIso = LocalDateTime.now().toString();

        for (Map.Entry<String, Integer> entry : stockMap.entrySet()) {
            String bg = entry.getKey();
            int qty = entry.getValue();
            boolean changed = setStockForGroup(inventory, bg, qty);
            if (changed) {
                lastUpdatedMap.put(bg, nowIso);
            }
        }

        // If client provided explicit lastUpdated timestamps, merge them
        if (request.getLastUpdated() != null) {
            for (Map.Entry<String, String> entry : request.getLastUpdated().entrySet()) {
                if (VALID_BLOOD_GROUPS.contains(entry.getKey()) && entry.getValue() != null) {
                    lastUpdatedMap.put(entry.getKey(), entry.getValue());
                }
            }
        }

        try {
            inventory.setLastUpdatedJson(objectMapper.writeValueAsString(lastUpdatedMap));
        } catch (Exception e) {
            inventory.setLastUpdatedJson("{}");
        }

        Inventory saved = inventoryRepository.save(inventory);
        return mapToDTO(saved);
    }

    private boolean setStockForGroup(Inventory inv, String bg, int qty) {
        boolean changed = false;
        switch (bg) {
            case "A+":
                changed = !Objects.equals(inv.getStockAPos(), qty);
                inv.setStockAPos(qty);
                break;
            case "A-":
                changed = !Objects.equals(inv.getStockANeg(), qty);
                inv.setStockANeg(qty);
                break;
            case "B+":
                changed = !Objects.equals(inv.getStockBPos(), qty);
                inv.setStockBPos(qty);
                break;
            case "B-":
                changed = !Objects.equals(inv.getStockBNeg(), qty);
                inv.setStockBNeg(qty);
                break;
            case "O+":
                changed = !Objects.equals(inv.getStockOPos(), qty);
                inv.setStockOPos(qty);
                break;
            case "O-":
                changed = !Objects.equals(inv.getStockONeg(), qty);
                inv.setStockONeg(qty);
                break;
            case "AB+":
                changed = !Objects.equals(inv.getStockAbPos(), qty);
                inv.setStockAbPos(qty);
                break;
            case "AB-":
                changed = !Objects.equals(inv.getStockAbNeg(), qty);
                inv.setStockAbNeg(qty);
                break;
        }
        return changed;
    }

    private InventoryDTO mapToDTO(Inventory inv) {
        Map<String, Integer> stock = new LinkedHashMap<>();
        stock.put("A+", inv.getStockAPos() != null ? inv.getStockAPos() : 0);
        stock.put("A-", inv.getStockANeg() != null ? inv.getStockANeg() : 0);
        stock.put("B+", inv.getStockBPos() != null ? inv.getStockBPos() : 0);
        stock.put("B-", inv.getStockBNeg() != null ? inv.getStockBNeg() : 0);
        stock.put("O+", inv.getStockOPos() != null ? inv.getStockOPos() : 0);
        stock.put("O-", inv.getStockONeg() != null ? inv.getStockONeg() : 0);
        stock.put("AB+", inv.getStockAbPos() != null ? inv.getStockAbPos() : 0);
        stock.put("AB-", inv.getStockAbNeg() != null ? inv.getStockAbNeg() : 0);

        Map<String, Integer> reservedStock = new LinkedHashMap<>();
        for (String bg : VALID_BLOOD_GROUPS) {
            reservedStock.put(bg, 0);
        }
        if (requestBloodBankRepository != null && inv.getBloodBankId() != null) {
            List<RequestBloodBank> matches = requestBloodBankRepository.findByBloodBankId(inv.getBloodBankId());
            for (RequestBloodBank rbb : matches) {
                if ("accepted".equalsIgnoreCase(rbb.getResponseStatus()) && rbb.getReservedUnits() != null && rbb.getReservedUnits() > 0) {
                    String bg = rbb.getBloodRequest() != null ? rbb.getBloodRequest().getBloodGroup() : null;
                    if (bg != null && reservedStock.containsKey(bg)) {
                        reservedStock.put(bg, reservedStock.get(bg) + rbb.getReservedUnits());
                    }
                }
            }
        }

        Map<String, String> lastUpdated = parseLastUpdated(inv.getLastUpdatedJson());
        for (String bg : VALID_BLOOD_GROUPS) {
            lastUpdated.putIfAbsent(bg, null);
        }

        return new InventoryDTO(inv.getBloodBankId(), stock, reservedStock, lastUpdated);
    }

    @Transactional(readOnly = true)
    public int getStockByGroup(String bloodBankId, String bloodGroup) {
        if (bloodGroup == null || !VALID_BLOOD_GROUPS.contains(bloodGroup.trim())) {
            throw new IllegalArgumentException("INVALID_BLOOD_GROUP: " + bloodGroup);
        }
        InventoryDTO dto = getInventory(bloodBankId);
        return dto.getStock().getOrDefault(bloodGroup.trim(), 0);
    }

    @Transactional(readOnly = true)
    public long getInventoryUpdatesCount() {
        return inventoryRepository.findAll().stream()
                .filter(inv -> {
                    Map<String, String> map = parseLastUpdated(inv.getLastUpdatedJson());
                    return map.values().stream().anyMatch(Objects::nonNull);
                })
                .count();
    }

    private Map<String, String> parseLastUpdated(String json) {
        if (json == null || json.trim().isEmpty()) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, String>>() {});
        } catch (Exception e) {
            return new HashMap<>();
        }
    }
}
