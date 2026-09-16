package com.bloodlink.service;

import com.bloodlink.dto.BloodBankAlertDTO;
import com.bloodlink.dto.CreateBloodBankMatchesRequest;
import com.bloodlink.dto.MatchBloodBanksResponseDTO;
import com.bloodlink.dto.MatchedBloodBankItemDTO;
import com.bloodlink.entity.BloodRequest;
import com.bloodlink.entity.Inventory;
import com.bloodlink.entity.RequestBloodBank;
import com.bloodlink.entity.RequestDonor;
import com.bloodlink.entity.User;
import com.bloodlink.repository.BloodRequestRepository;
import com.bloodlink.repository.InventoryRepository;
import com.bloodlink.repository.RequestBloodBankRepository;
import com.bloodlink.repository.RequestDonorRepository;
import com.bloodlink.repository.UserRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class BloodBankRequestService {

    private final RequestBloodBankRepository requestBloodBankRepository;
    private final BloodRequestRepository bloodRequestRepository;
    private final RequestDonorRepository requestDonorRepository;
    private final UserRepository userRepository;
    private final InventoryRepository inventoryRepository;
    private final ObjectMapper objectMapper;

    @Autowired
    public BloodBankRequestService(RequestBloodBankRepository requestBloodBankRepository,
                                  BloodRequestRepository bloodRequestRepository,
                                  RequestDonorRepository requestDonorRepository,
                                  UserRepository userRepository,
                                  InventoryRepository inventoryRepository,
                                  ObjectMapper objectMapper) {
        this.requestBloodBankRepository = requestBloodBankRepository;
        this.bloodRequestRepository = bloodRequestRepository;
        this.requestDonorRepository = requestDonorRepository;
        this.userRepository = userRepository;
        this.inventoryRepository = inventoryRepository;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    @Transactional
    public void migrateLegacyWaitingRecords() {
        try {
            List<BloodRequest> activeRequests = bloodRequestRepository.findAll();
            for (BloodRequest br : activeRequests) {
                String status = br.getStatus() != null ? br.getStatus().trim().toLowerCase() : "";
                if ("open".equals(status) || "partially_fulfilled".equals(status)) {
                    List<RequestBloodBank> matches = requestBloodBankRepository.findByBloodRequestId(br.getId());
                    for (RequestBloodBank rbb : matches) {
                        if ("waiting".equalsIgnoreCase(rbb.getResponseStatus())) {
                            rbb.setResponseStatus("pending");
                            requestBloodBankRepository.save(rbb);
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    @Transactional
    public List<RequestBloodBank> createBloodBankMatches(String requestId, CreateBloodBankMatchesRequest req) {
        if (requestId == null || requestId.trim().isEmpty() || req == null || req.getBloodBanks() == null || req.getBloodBanks().isEmpty()) {
            throw new IllegalArgumentException("MISSING_MATCH_DATA");
        }

        BloodRequest br = bloodRequestRepository.findById(requestId.trim())
                .orElseThrow(() -> new IllegalArgumentException("REQUEST_NOT_FOUND"));

        List<RequestBloodBank> savedList = new ArrayList<>();

        for (CreateBloodBankMatchesRequest.BloodBankMatchItem item : req.getBloodBanks()) {
            if (item.getBloodBankId() == null || item.getBloodBankId().trim().isEmpty()) {
                continue;
            }

            String bbId = item.getBloodBankId().trim();
            User bankUser = userRepository.findById(bbId).orElse(null);
            if (bankUser == null || !"bloodbank".equalsIgnoreCase(bankUser.getRole())) {
                continue;
            }

            RequestBloodBank rbb = requestBloodBankRepository
                    .findByBloodBankIdAndBloodRequestId(bbId, requestId.trim())
                    .orElseGet(() -> {
                        RequestBloodBank newMatch = new RequestBloodBank();
                        newMatch.setBloodRequest(br);
                        newMatch.setBloodBankId(bbId);
                        return newMatch;
                    });

            rbb.setDistance(item.getDistance());
            rbb.setAvailableUnitsAtMatch(item.getAvailableUnitsAtMatch() != null ? item.getAvailableUnitsAtMatch() : 0);
            rbb.setStatus(item.getStatus() != null ? item.getStatus().trim() : "sufficient");
            rbb.setResponseStatus(item.getResponseStatus() != null ? item.getResponseStatus().trim() : "pending");
            rbb.setUnavailable(false);

            RequestBloodBank saved = requestBloodBankRepository.save(rbb);
            savedList.add(saved);
        }

        return savedList;
    }

    @Transactional(readOnly = true)
    public List<BloodBankAlertDTO> getBloodBankAlerts(String bloodBankId) {
        if (bloodBankId == null || bloodBankId.trim().isEmpty()) {
            throw new IllegalArgumentException("BLOOD_BANK_NOT_FOUND");
        }

        User user = userRepository.findById(bloodBankId.trim())
                .orElseThrow(() -> new IllegalArgumentException("BLOOD_BANK_NOT_FOUND"));

        if (!"bloodbank".equalsIgnoreCase(user.getRole())) {
            throw new IllegalArgumentException("NOT_A_BLOOD_BANK");
        }

        List<RequestBloodBank> matches = requestBloodBankRepository.findByBloodBankId(bloodBankId.trim());
        List<BloodBankAlertDTO> alertList = new ArrayList<>();

        for (RequestBloodBank rbb : matches) {
            BloodRequest br = rbb.getBloodRequest();
            if (br == null) continue;

            User hospital = userRepository.findById(br.getHospitalId()).orElse(null);

            BloodBankAlertDTO dto = new BloodBankAlertDTO();
            dto.setId(br.getId());
            dto.setHospitalId(br.getHospitalId());
            dto.setHospitalName(hospital != null ? hospital.getName() : "Hospital");
            dto.setHospitalCity(hospital != null ? hospital.getCity() : "");
            dto.setHospitalPhone(hospital != null ? hospital.getPhone() : br.getContact());
            dto.setPatientName(br.getPatientName());
            dto.setBloodGroup(br.getBloodGroup());
            dto.setUnits(br.getUnits());
            dto.setUrgency(br.getUrgency());
            dto.setContact(br.getContact());
            dto.setNotes(br.getNotes());
            dto.setStatus(br.getStatus());
            dto.setRadius(br.getRadius());
            dto.setRequestType(br.getRequestType());
            dto.setReason(br.getReason());
            dto.setOperationTime(br.getOperationTime() != null ? br.getOperationTime().toString() : null);
            dto.setAccumulationDeadline(br.getAccumulationDeadline() != null ? br.getAccumulationDeadline().toString() : null);
            dto.setCreatedAt(br.getCreatedAt() != null ? br.getCreatedAt().toString() : null);
            dto.setBankStatus(rbb.getResponseStatus());
            dto.setBankRespondedAt(rbb.getRespondedAt() != null ? rbb.getRespondedAt().toString() : null);

            Map<String, Object> bm = new HashMap<>();
            bm.put("id", bloodBankId);
            bm.put("distance", rbb.getDistance());
            bm.put("bloodGroup", br.getBloodGroup());
            bm.put("availableUnitsAtMatch", rbb.getAvailableUnitsAtMatch());
            bm.put("status", rbb.getStatus());
            bm.put("responseStatus", rbb.getResponseStatus());
            bm.put("unitsSecured", rbb.getUnitsSecured() != null ? rbb.getUnitsSecured() : 0);
            bm.put("reservedUnits", rbb.getReservedUnits() != null ? rbb.getReservedUnits() : 0);
            bm.put("respondedAt", rbb.getRespondedAt() != null ? rbb.getRespondedAt().toString() : null);
            dto.setBankMatch(bm);

            alertList.add(dto);
        }

        alertList.sort((a, b) -> {
            if (a.getCreatedAt() == null || b.getCreatedAt() == null) return 0;
            return b.getCreatedAt().compareTo(a.getCreatedAt());
        });

        return alertList;
    }

    @Transactional
    public Map<String, Object> respondToAlert(String bloodBankId, String requestId, String newStatus) {
        if (bloodBankId == null || requestId == null || newStatus == null) {
            throw new IllegalArgumentException("INVALID_STATUS");
        }

        String normalizedStatus = newStatus.trim().toLowerCase();
        if ("declined".equals(normalizedStatus)) {
            normalizedStatus = "rejected";
        }

        if (!"accepted".equals(normalizedStatus) && !"rejected".equals(normalizedStatus)) {
            throw new IllegalArgumentException("INVALID_STATUS");
        }

        User user = userRepository.findById(bloodBankId.trim())
                .orElseThrow(() -> new IllegalArgumentException("BLOOD_BANK_NOT_FOUND"));

        if (!"bloodbank".equalsIgnoreCase(user.getRole())) {
            throw new IllegalArgumentException("NOT_A_BLOOD_BANK");
        }

        RequestBloodBank rbb = requestBloodBankRepository
                .findByBloodBankIdAndBloodRequestId(bloodBankId.trim(), requestId.trim())
                .orElseThrow(() -> new IllegalArgumentException("ALERT_NOT_FOUND"));

        BloodRequest br = rbb.getBloodRequest();
        if (br == null) {
            br = bloodRequestRepository.findById(requestId.trim())
                    .orElseThrow(() -> new IllegalArgumentException("REQUEST_NOT_FOUND"));
        }

        if ("SCHEDULED".equalsIgnoreCase(br.getRequestType()) && br.getAccumulationDeadline() != null) {
            if (LocalDateTime.now().isAfter(br.getAccumulationDeadline())) {
                throw new IllegalArgumentException("ACCUMULATION_DEADLINE_PASSED");
            }
        }

        Map<String, Object> response = new HashMap<>();

        if ("accepted".equalsIgnoreCase(normalizedStatus)) {
            int currentSecured = br.getSecuredUnits() != null ? br.getSecuredUnits() : 0;
            int needed = Math.max(1, br.getUnits() - currentSecured);

            if (currentSecured >= br.getUnits() || "fulfilled".equalsIgnoreCase(br.getStatus()) || "closed".equalsIgnoreCase(rbb.getResponseStatus())) {
                rbb.setResponseStatus("closed");
                rbb.setUnavailable(true);
                requestBloodBankRepository.save(rbb);
                throw new IllegalArgumentException("REQUEST_ALREADY_FULFILLED");
            }

            Inventory inv = inventoryRepository.findByBloodBankId(bloodBankId.trim()).orElse(null);
            List<String> compatGroups = getOrderedCompatibleGroups(br.getBloodGroup());
            Map<String, Integer> allocatedMap = new LinkedHashMap<>();
            int remainingNeeded = needed;

            if (inv != null) {
                for (String g : compatGroups) {
                    if (remainingNeeded <= 0) break;
                    int currentStock = getStockForGroup(inv, g);
                    if (currentStock > 0) {
                        int take = Math.min(remainingNeeded, currentStock);
                        setStockForGroup(inv, g, currentStock - take);
                        allocatedMap.put(g, take);
                        remainingNeeded -= take;
                    }
                }
                if (!allocatedMap.isEmpty()) {
                    inv.setLastUpdatedJson(LocalDateTime.now().toString());
                    inventoryRepository.save(inv);
                }
            }

            int reserveUnits = needed - remainingNeeded;
            String allocatedJson = null;
            try {
                allocatedJson = objectMapper.writeValueAsString(allocatedMap);
            } catch (Exception e) {
                allocatedJson = "{}";
            }

            rbb.setResponseStatus("accepted");
            rbb.setReservedUnits(reserveUnits);
            rbb.setUnitsSecured(reserveUnits);
            rbb.setReservedGroupsJson(allocatedJson);
            rbb.setRespondedAt(LocalDateTime.now());
            rbb.setUnavailable(true);
            requestBloodBankRepository.save(rbb);

            response.put("status", "accepted");
            response.put("unitsSecured", reserveUnits);
            response.put("reservedUnits", reserveUnits);
            response.put("requestStatus", br.getStatus());
            response.put("totalSecuredUnits", br.getSecuredUnits() != null ? br.getSecuredUnits() : 0);
            response.put("requiredUnits", br.getUnits());
            response.put("reservedGroups", allocatedMap);
        } else {
            // Rejected
            restoreReservedInventory(bloodBankId.trim(), rbb, br.getBloodGroup());

            rbb.setResponseStatus("rejected");
            rbb.setReservedUnits(0);
            rbb.setUnitsSecured(0);
            rbb.setReservedGroupsJson(null);
            rbb.setRespondedAt(LocalDateTime.now());
            rbb.setUnavailable(true);
            requestBloodBankRepository.save(rbb);

            response.put("status", "rejected");
            response.put("unitsSecured", 0);
            response.put("reservedUnits", 0);
            response.put("requestStatus", br.getStatus());
        }

        return response;
    }

    @Transactional
    public BloodRequest confirmBloodBank(String requestId, String bloodBankId, String hospitalId) {
        if (requestId == null || requestId.trim().isEmpty() || bloodBankId == null || bloodBankId.trim().isEmpty()) {
            throw new IllegalArgumentException("REQUEST_NOT_FOUND");
        }

        BloodRequest br = bloodRequestRepository.findById(requestId.trim())
                .orElseThrow(() -> new IllegalArgumentException("REQUEST_NOT_FOUND"));

        if (hospitalId != null && !hospitalId.trim().isEmpty() && !hospitalId.equals(br.getHospitalId())) {
            throw new IllegalArgumentException("UNAUTHORIZED");
        }

        User bbUser = userRepository.findById(bloodBankId.trim())
                .orElseThrow(() -> new IllegalArgumentException("BLOOD_BANK_NOT_FOUND"));
        if (!"bloodbank".equalsIgnoreCase(bbUser.getRole())) {
            throw new IllegalArgumentException("NOT_A_BLOOD_BANK");
        }

        if ("SCHEDULED".equalsIgnoreCase(br.getRequestType()) && br.getAccumulationDeadline() != null) {
            if (LocalDateTime.now().isAfter(br.getAccumulationDeadline())) {
                throw new IllegalArgumentException("ACCUMULATION_DEADLINE_PASSED");
            }
        }

        RequestBloodBank rbb = requestBloodBankRepository
                .findByBloodBankIdAndBloodRequestId(bloodBankId.trim(), requestId.trim())
                .orElseThrow(() -> new IllegalArgumentException("ALERT_NOT_FOUND"));

        if ("confirmed".equalsIgnoreCase(rbb.getResponseStatus())) {
            return br;
        }

        if ("rejected".equalsIgnoreCase(rbb.getResponseStatus())) {
            throw new IllegalArgumentException("ALREADY_REJECTED");
        }

        if (!"accepted".equalsIgnoreCase(rbb.getResponseStatus())) {
            throw new IllegalArgumentException("BLOOD_BANK_NOT_ACCEPTED");
        }

        if ("fulfilled".equalsIgnoreCase(br.getStatus()) || (br.getSecuredUnits() != null && br.getSecuredUnits() >= br.getUnits())) {
            throw new IllegalArgumentException("REQUEST_ALREADY_FULFILLED");
        }

        int otherBankUnits = requestBloodBankRepository.findByBloodRequestId(requestId.trim()).stream()
                .filter(b -> "confirmed".equalsIgnoreCase(b.getResponseStatus()) && !b.getId().equals(rbb.getId()))
                .mapToInt(b -> b.getUnitsSecured() != null ? b.getUnitsSecured() : 0)
                .sum();

        int donorUnits = (int) requestDonorRepository.findByBloodRequestId(requestId.trim()).stream()
                .filter(d -> "confirmed".equalsIgnoreCase(d.getStatus()))
                .count();

        int remainingNeeded = Math.max(0, br.getUnits() - (otherBankUnits + donorUnits));
        int reserved = rbb.getReservedUnits() != null ? rbb.getReservedUnits() : (rbb.getUnitsSecured() != null ? rbb.getUnitsSecured() : 0);
        int avail = rbb.getAvailableUnitsAtMatch() != null && rbb.getAvailableUnitsAtMatch() > 0 ? rbb.getAvailableUnitsAtMatch() : 1;
        int unitsToSecure = Math.min(reserved > 0 ? reserved : avail, remainingNeeded);

        if (reserved > unitsToSecure) {
            int excessToRestore = reserved - unitsToSecure;
            restorePartialReservedInventory(bloodBankId.trim(), rbb, excessToRestore, br.getBloodGroup());
        }

        rbb.setResponseStatus("confirmed");
        rbb.setRespondedAt(LocalDateTime.now());
        rbb.setReservedUnits(0);
        rbb.setUnitsSecured(unitsToSecure);
        requestBloodBankRepository.save(rbb);

        int totalSecured = otherBankUnits + donorUnits + unitsToSecure;
        br.setSecuredUnits(totalSecured);

        if (totalSecured >= br.getUnits()) {
            br.setStatus("fulfilled");
            br.setFulfilledAt(LocalDateTime.now());
            closeRemainingWaitingBanks(requestId.trim(), rbb.getId(), br.getBloodGroup());
            List<RequestDonor> allDonors = requestDonorRepository.findByBloodRequestId(requestId.trim());
            for (RequestDonor rd : allDonors) {
                if ("pending".equalsIgnoreCase(rd.getStatus())) {
                    rd.setStatus("closed");
                    requestDonorRepository.save(rd);
                }
            }
        } else {
            br.setStatus("partially_fulfilled");
        }

        return bloodRequestRepository.save(br);
    }

    @Transactional
    public BloodRequest rejectBloodBank(String requestId, String bloodBankId, String hospitalId) {
        if (requestId == null || requestId.trim().isEmpty() || bloodBankId == null || bloodBankId.trim().isEmpty()) {
            throw new IllegalArgumentException("REQUEST_NOT_FOUND");
        }

        BloodRequest br = bloodRequestRepository.findById(requestId.trim())
                .orElseThrow(() -> new IllegalArgumentException("REQUEST_NOT_FOUND"));

        if (hospitalId != null && !hospitalId.trim().isEmpty() && !hospitalId.equals(br.getHospitalId())) {
            throw new IllegalArgumentException("UNAUTHORIZED");
        }

        User bbUser = userRepository.findById(bloodBankId.trim())
                .orElseThrow(() -> new IllegalArgumentException("BLOOD_BANK_NOT_FOUND"));
        if (!"bloodbank".equalsIgnoreCase(bbUser.getRole())) {
            throw new IllegalArgumentException("NOT_A_BLOOD_BANK");
        }

        RequestBloodBank rbb = requestBloodBankRepository
                .findByBloodBankIdAndBloodRequestId(bloodBankId.trim(), requestId.trim())
                .orElseThrow(() -> new IllegalArgumentException("ALERT_NOT_FOUND"));

        if ("rejected".equalsIgnoreCase(rbb.getResponseStatus())) {
            return br;
        }

        if ("confirmed".equalsIgnoreCase(rbb.getResponseStatus())) {
            throw new IllegalArgumentException("ALREADY_CONFIRMED");
        }

        if (!"accepted".equalsIgnoreCase(rbb.getResponseStatus())) {
            throw new IllegalArgumentException("BLOOD_BANK_NOT_ACCEPTED");
        }

        restoreReservedInventory(bloodBankId.trim(), rbb, br.getBloodGroup());

        rbb.setResponseStatus("rejected");
        rbb.setReservedUnits(0);
        rbb.setUnitsSecured(0);
        rbb.setReservedGroupsJson(null);
        rbb.setUnavailable(true);
        rbb.setRespondedAt(LocalDateTime.now());
        requestBloodBankRepository.save(rbb);

        int bankUnits = requestBloodBankRepository.findByBloodRequestId(requestId.trim()).stream()
                .filter(b -> "confirmed".equalsIgnoreCase(b.getResponseStatus()))
                .mapToInt(b -> b.getUnitsSecured() != null ? b.getUnitsSecured() : 0)
                .sum();

        int donorUnits = (int) requestDonorRepository.findByBloodRequestId(requestId.trim()).stream()
                .filter(d -> "confirmed".equalsIgnoreCase(d.getStatus()))
                .count();

        int totalSecured = bankUnits + donorUnits;
        br.setSecuredUnits(totalSecured);

        if (totalSecured == 0 && "partially_fulfilled".equalsIgnoreCase(br.getStatus())) {
            br.setStatus("open");
        }

        return bloodRequestRepository.save(br);
    }

    private void activateNextWaitingBank(String requestId) {
        List<RequestBloodBank> allMatches = requestBloodBankRepository.findByBloodRequestIdOrderByIdAsc(requestId);
        for (RequestBloodBank mb : allMatches) {
            if ("waiting".equalsIgnoreCase(mb.getResponseStatus()) && !Boolean.TRUE.equals(mb.getUnavailable())) {
                mb.setResponseStatus("pending");
                requestBloodBankRepository.save(mb);
                break;
            }
        }
    }

    private void closeRemainingWaitingBanks(String requestId, Long acceptedId, String bloodGroup) {
        List<RequestBloodBank> allMatches = requestBloodBankRepository.findByBloodRequestId(requestId);
        for (RequestBloodBank mb : allMatches) {
            if (!mb.getId().equals(acceptedId) && !Boolean.TRUE.equals(mb.getUnavailable())) {
                if ("accepted".equalsIgnoreCase(mb.getResponseStatus()) && mb.getReservedUnits() != null && mb.getReservedUnits() > 0) {
                    restoreReservedInventory(mb.getBloodBankId(), mb, bloodGroup);
                    mb.setReservedUnits(0);
                    mb.setUnitsSecured(0);
                }
                mb.setResponseStatus("closed");
                mb.setUnavailable(true);
                requestBloodBankRepository.save(mb);
            }
        }
    }

    private int getStockForGroup(Inventory inv, String bg) {
        if (inv == null || bg == null) return 0;
        Integer val = switch (bg) {
            case "A+" -> inv.getStockAPos();
            case "A-" -> inv.getStockANeg();
            case "B+" -> inv.getStockBPos();
            case "B-" -> inv.getStockBNeg();
            case "O+" -> inv.getStockOPos();
            case "O-" -> inv.getStockONeg();
            case "AB+" -> inv.getStockAbPos();
            case "AB-" -> inv.getStockAbNeg();
            default -> 0;
        };
        return val != null ? Math.max(0, val) : 0;
    }

    private void setStockForGroup(Inventory inv, String bg, int qty) {
        int safeQty = Math.max(0, qty);
        switch (bg) {
            case "A+" -> inv.setStockAPos(safeQty);
            case "A-" -> inv.setStockANeg(safeQty);
            case "B+" -> inv.setStockBPos(safeQty);
            case "B-" -> inv.setStockBNeg(safeQty);
            case "O+" -> inv.setStockOPos(safeQty);
            case "O-" -> inv.setStockONeg(safeQty);
            case "AB+" -> inv.setStockAbPos(safeQty);
            case "AB-" -> inv.setStockAbNeg(safeQty);
        }
    }

    private static Double calculateDistance(Double la1, Double lo1, Double la2, Double lo2) {
        if (la1 == null || lo1 == null || la2 == null || lo2 == null) return null;
        final double R = 6371.0;
        double d1 = ((la2 - la1) * Math.PI) / 180.0;
        double d2 = ((lo2 - lo1) * Math.PI) / 180.0;
        double a = Math.sin(d1 / 2) * Math.sin(d1 / 2) +
                   Math.cos((la1 * Math.PI) / 180.0) *
                   Math.cos((la2 * Math.PI) / 180.0) *
                   Math.sin(d2 / 2) * Math.sin(d2 / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private static final Map<String, List<String>> ORDERED_COMPAT_MAP = new HashMap<>();
    static {
        ORDERED_COMPAT_MAP.put("O-", Arrays.asList("O-"));
        ORDERED_COMPAT_MAP.put("O+", Arrays.asList("O+", "O-"));
        ORDERED_COMPAT_MAP.put("A-", Arrays.asList("A-", "O-"));
        ORDERED_COMPAT_MAP.put("A+", Arrays.asList("A+", "O+", "A-", "O-"));
        ORDERED_COMPAT_MAP.put("B-", Arrays.asList("B-", "O-"));
        ORDERED_COMPAT_MAP.put("B+", Arrays.asList("B+", "O+", "B-", "O-"));
        ORDERED_COMPAT_MAP.put("AB-", Arrays.asList("AB-", "A-", "B-", "O-"));
        ORDERED_COMPAT_MAP.put("AB+", Arrays.asList("AB+", "A+", "B+", "O+", "AB-", "A-", "B-", "O-"));
    }

    public static List<String> getOrderedCompatibleGroups(String reqBg) {
        if (reqBg == null || reqBg.trim().isEmpty()) return Collections.emptyList();
        String normalized = reqBg.trim().toUpperCase();
        return ORDERED_COMPAT_MAP.getOrDefault(normalized, Collections.singletonList(normalized));
    }

    private void restoreReservedInventory(String bloodBankId, RequestBloodBank rbb, String fallbackBloodGroup) {
        if (rbb == null) return;
        Inventory inv = inventoryRepository.findByBloodBankId(bloodBankId).orElse(null);
        if (inv == null) return;

        boolean updated = false;
        String json = rbb.getReservedGroupsJson();
        if (json != null && !json.trim().isEmpty() && !json.trim().equals("{}")) {
            try {
                Map<String, Integer> allocatedMap = objectMapper.readValue(json, new TypeReference<Map<String, Integer>>() {});
                for (Map.Entry<String, Integer> entry : allocatedMap.entrySet()) {
                    String g = entry.getKey();
                    int qty = entry.getValue() != null ? entry.getValue() : 0;
                    if (qty > 0) {
                        int cur = getStockForGroup(inv, g);
                        setStockForGroup(inv, g, cur + qty);
                        updated = true;
                    }
                }
            } catch (Exception e) {
                int toRestore = rbb.getReservedUnits() != null ? rbb.getReservedUnits() : 0;
                if (toRestore > 0) {
                    int cur = getStockForGroup(inv, fallbackBloodGroup);
                    setStockForGroup(inv, fallbackBloodGroup, cur + toRestore);
                    updated = true;
                }
            }
        } else {
            int toRestore = rbb.getReservedUnits() != null ? rbb.getReservedUnits() : 0;
            if (toRestore > 0) {
                int cur = getStockForGroup(inv, fallbackBloodGroup);
                setStockForGroup(inv, fallbackBloodGroup, cur + toRestore);
                updated = true;
            }
        }

        if (updated) {
            inv.setLastUpdatedJson(LocalDateTime.now().toString());
            inventoryRepository.save(inv);
        }
    }

    private void restorePartialReservedInventory(String bloodBankId, RequestBloodBank rbb, int excessToRestore, String fallbackBloodGroup) {
        if (rbb == null || excessToRestore <= 0) return;
        Inventory inv = inventoryRepository.findByBloodBankId(bloodBankId).orElse(null);
        if (inv == null) return;

        boolean updated = false;
        String json = rbb.getReservedGroupsJson();
        int remaining = excessToRestore;

        if (json != null && !json.trim().isEmpty() && !json.trim().equals("{}")) {
            try {
                Map<String, Integer> allocatedMap = objectMapper.readValue(json, new TypeReference<Map<String, Integer>>() {});
                for (Map.Entry<String, Integer> entry : allocatedMap.entrySet()) {
                    if (remaining <= 0) break;
                    String g = entry.getKey();
                    int qty = entry.getValue() != null ? entry.getValue() : 0;
                    if (qty > 0) {
                        int restoreQty = Math.min(remaining, qty);
                        int cur = getStockForGroup(inv, g);
                        setStockForGroup(inv, g, cur + restoreQty);
                        remaining -= restoreQty;
                        updated = true;
                    }
                }
            } catch (Exception e) {
                int cur = getStockForGroup(inv, fallbackBloodGroup);
                setStockForGroup(inv, fallbackBloodGroup, cur + excessToRestore);
                updated = true;
            }
        } else {
            int cur = getStockForGroup(inv, fallbackBloodGroup);
            setStockForGroup(inv, fallbackBloodGroup, cur + excessToRestore);
            updated = true;
        }

        if (updated) {
            inv.setLastUpdatedJson(LocalDateTime.now().toString());
            inventoryRepository.save(inv);
        }
    }

    @Transactional(readOnly = true)
    public MatchBloodBanksResponseDTO matchBloodBanksForRequest(String requestId) {
        if (requestId == null || requestId.trim().isEmpty()) {
            throw new IllegalArgumentException("REQUEST_NOT_FOUND");
        }

        BloodRequest br = bloodRequestRepository.findById(requestId.trim())
                .orElseThrow(() -> new IllegalArgumentException("REQUEST_NOT_FOUND"));

        String reqBloodGroup = br.getBloodGroup();
        int reqUnits = br.getUnits() != null && br.getUnits() > 0 ? br.getUnits() : 1;
        int radius = br.getRadius() != null && br.getRadius() > 0 ? br.getRadius() : 15;

        User hospital = br.getHospitalId() != null ? userRepository.findById(br.getHospitalId().trim()).orElse(null) : null;
        if (hospital == null || hospital.getLat() == null || hospital.getLng() == null) {
            return new MatchBloodBanksResponseDTO(Collections.emptyList(), 0);
        }

        double hLat = hospital.getLat();
        double hLng = hospital.getLng();

        List<User> bloodBanks = userRepository.findByRoleIgnoreCaseAndLatIsNotNullAndLngIsNotNull("bloodbank");
        List<MatchedBloodBankItemDTO> candidates = new ArrayList<>();
        List<String> compatGroups = getOrderedCompatibleGroups(reqBloodGroup);

        for (User bb : bloodBanks) {
            Double d = calculateDistance(hLat, hLng, bb.getLat(), bb.getLng());
            if (d == null || d > radius) {
                continue;
            }

            Inventory inv = inventoryRepository.findByBloodBankId(bb.getId()).orElse(null);
            if (inv == null) {
                continue;
            }

            Map<String, Integer> compatibleStock = new LinkedHashMap<>();
            int totalCompatibleStock = 0;
            for (String g : compatGroups) {
                int stock = getStockForGroup(inv, g);
                if (stock > 0) {
                    compatibleStock.put(g, stock);
                    totalCompatibleStock += stock;
                }
            }

            if (totalCompatibleStock <= 0) {
                continue;
            }

            double roundedDist = Math.round(d * 10.0) / 10.0;
            String status = totalCompatibleStock >= reqUnits ? "sufficient" : "partial";

            MatchedBloodBankItemDTO item = new MatchedBloodBankItemDTO();
            item.setId(bb.getId());
            item.setName(bb.getName());
            item.setRegistrationNumber(bb.getRegistrationNumber());
            item.setPhone(bb.getPhone());
            item.setAddress(null);
            item.setCity(bb.getCity());
            item.setState(bb.getState());
            item.setDistance(roundedDist);
            item.setBloodGroup(reqBloodGroup);
            item.setAvailableUnitsAtMatch(totalCompatibleStock);
            item.setCompatibleStock(compatibleStock);
            item.setStatus(status);
            item.setUnitsSecured(0);
            item.setRespondedAt(null);

            candidates.add(item);
        }

        candidates.sort((a, b) -> {
            boolean aSuff = "sufficient".equalsIgnoreCase(a.getStatus());
            boolean bSuff = "sufficient".equalsIgnoreCase(b.getStatus());
            if (aSuff && !bSuff) return -1;
            if (!aSuff && bSuff) return 1;
            return Double.compare(a.getDistance(), b.getDistance());
        });

        for (int i = 0; i < candidates.size(); i++) {
            candidates.get(i).setResponseStatus("pending");
        }

        return new MatchBloodBanksResponseDTO(candidates, candidates.size());
    }

    @Transactional
    public int expandBloodBankMatches(BloodRequest br, int newRadius) {
        if (br == null || br.getId() == null) {
            return 0;
        }

        String reqBloodGroup = br.getBloodGroup();
        int reqUnits = br.getUnits() != null && br.getUnits() > 0 ? br.getUnits() : 1;

        User hospital = br.getHospitalId() != null ? userRepository.findById(br.getHospitalId().trim()).orElse(null) : null;
        if (hospital == null || hospital.getLat() == null || hospital.getLng() == null) {
            return 0;
        }

        double hLat = hospital.getLat();
        double hLng = hospital.getLng();

        List<User> bloodBanks = userRepository.findByRoleIgnoreCaseAndLatIsNotNullAndLngIsNotNull("bloodbank");
        List<MatchedBloodBankItemDTO> candidates = new ArrayList<>();
        List<String> compatGroups = getOrderedCompatibleGroups(reqBloodGroup);

        for (User bb : bloodBanks) {
            Double d = calculateDistance(hLat, hLng, bb.getLat(), bb.getLng());
            if (d == null || d > newRadius) {
                continue;
            }

            Inventory inv = inventoryRepository.findByBloodBankId(bb.getId()).orElse(null);
            if (inv == null) {
                continue;
            }

            Map<String, Integer> compatibleStock = new LinkedHashMap<>();
            int totalCompatibleStock = 0;
            for (String g : compatGroups) {
                int stock = getStockForGroup(inv, g);
                if (stock > 0) {
                    compatibleStock.put(g, stock);
                    totalCompatibleStock += stock;
                }
            }

            if (totalCompatibleStock <= 0) {
                continue;
            }

            double roundedDist = Math.round(d * 10.0) / 10.0;
            String status = totalCompatibleStock >= reqUnits ? "sufficient" : "partial";

            MatchedBloodBankItemDTO item = new MatchedBloodBankItemDTO();
            item.setId(bb.getId());
            item.setName(bb.getName());
            item.setRegistrationNumber(bb.getRegistrationNumber());
            item.setPhone(bb.getPhone());
            item.setAddress(null);
            item.setCity(bb.getCity());
            item.setState(bb.getState());
            item.setDistance(roundedDist);
            item.setBloodGroup(reqBloodGroup);
            item.setAvailableUnitsAtMatch(totalCompatibleStock);
            item.setCompatibleStock(compatibleStock);
            item.setStatus(status);
            item.setUnitsSecured(0);
            item.setRespondedAt(null);

            candidates.add(item);
        }

        candidates.sort((a, b) -> {
            boolean aSuff = "sufficient".equalsIgnoreCase(a.getStatus());
            boolean bSuff = "sufficient".equalsIgnoreCase(b.getStatus());
            if (aSuff && !bSuff) return -1;
            if (!aSuff && bSuff) return 1;
            return Double.compare(a.getDistance(), b.getDistance());
        });

        List<RequestBloodBank> existingMatches = requestBloodBankRepository.findByBloodRequestId(br.getId());
        Set<String> existingBbIds = new HashSet<>();
        for (RequestBloodBank emb : existingMatches) {
            if (emb.getBloodBankId() != null) {
                existingBbIds.add(emb.getBloodBankId());
            }
        }

        int newAddedCount = 0;
        for (MatchedBloodBankItemDTO candidate : candidates) {
            if (existingBbIds.contains(candidate.getId())) {
                continue; // Strictly preserve existing records and their statuses
            }

            RequestBloodBank newMatch = new RequestBloodBank();
            newMatch.setBloodRequest(br);
            newMatch.setBloodBankId(candidate.getId());
            newMatch.setDistance(candidate.getDistance());
            newMatch.setAvailableUnitsAtMatch(candidate.getAvailableUnitsAtMatch());
            newMatch.setStatus(candidate.getStatus());
            newMatch.setResponseStatus("pending");
            newMatch.setUnitsSecured(0);
            newMatch.setReservedUnits(0);
            newMatch.setUnavailable(false);
            requestBloodBankRepository.save(newMatch);

            existingBbIds.add(candidate.getId());
            newAddedCount++;
        }

        return newAddedCount;
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
