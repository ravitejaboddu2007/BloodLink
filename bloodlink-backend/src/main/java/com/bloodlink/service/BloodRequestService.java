package com.bloodlink.service;

import com.bloodlink.dto.CreateBloodRequestRequest;
import com.bloodlink.dto.CreateDonorAlertsRequest;
import com.bloodlink.dto.HospitalRequestDTO;
import com.bloodlink.dto.MatchedDonorItemDTO;
import com.bloodlink.dto.MatchDonorsResponseDTO;
import com.bloodlink.entity.BloodRequest;
import com.bloodlink.entity.DonationHistory;
import com.bloodlink.entity.Inventory;
import com.bloodlink.entity.RequestBloodBank;
import com.bloodlink.entity.RequestDonor;
import com.bloodlink.entity.User;
import com.bloodlink.repository.BloodRequestRepository;
import com.bloodlink.repository.DonationHistoryRepository;
import com.bloodlink.repository.InventoryRepository;
import com.bloodlink.repository.RequestBloodBankRepository;
import com.bloodlink.repository.RequestDonorRepository;
import com.bloodlink.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class BloodRequestService {

    private static final List<String> VALID_BLOOD_GROUPS = Arrays.asList(
            "A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-"
    );

    private final BloodRequestRepository bloodRequestRepository;
    private final RequestDonorRepository requestDonorRepository;
    private final RequestBloodBankRepository requestBloodBankRepository;
    private final DonationHistoryRepository donationHistoryRepository;
    private final UserRepository userRepository;
    private final BloodBankRequestService bloodBankRequestService;
    private final InventoryRepository inventoryRepository;

    public BloodRequestService(BloodRequestRepository bloodRequestRepository,
                               RequestDonorRepository requestDonorRepository,
                               RequestBloodBankRepository requestBloodBankRepository,
                               DonationHistoryRepository donationHistoryRepository,
                               UserRepository userRepository) {
        this(bloodRequestRepository, requestDonorRepository, requestBloodBankRepository, donationHistoryRepository, userRepository, null, null);
    }

    public BloodRequestService(BloodRequestRepository bloodRequestRepository,
                               RequestDonorRepository requestDonorRepository,
                               RequestBloodBankRepository requestBloodBankRepository,
                               DonationHistoryRepository donationHistoryRepository,
                               UserRepository userRepository,
                               BloodBankRequestService bloodBankRequestService) {
        this(bloodRequestRepository, requestDonorRepository, requestBloodBankRepository, donationHistoryRepository, userRepository, bloodBankRequestService, null);
    }

    @Autowired
    public BloodRequestService(BloodRequestRepository bloodRequestRepository,
                               RequestDonorRepository requestDonorRepository,
                               RequestBloodBankRepository requestBloodBankRepository,
                               DonationHistoryRepository donationHistoryRepository,
                               UserRepository userRepository,
                               BloodBankRequestService bloodBankRequestService,
                               InventoryRepository inventoryRepository) {
        this.bloodRequestRepository = bloodRequestRepository;
        this.requestDonorRepository = requestDonorRepository;
        this.requestBloodBankRepository = requestBloodBankRepository;
        this.donationHistoryRepository = donationHistoryRepository;
        this.userRepository = userRepository;
        this.bloodBankRequestService = bloodBankRequestService;
        this.inventoryRepository = inventoryRepository;
    }

    @Transactional
    public BloodRequest createRequest(CreateBloodRequestRequest req) {
        if (req == null) {
            throw new IllegalArgumentException("Required request fields missing");
        }

        if (req.getHospitalId() == null || req.getHospitalId().trim().isEmpty()) {
            throw new IllegalArgumentException("HOSPITAL_NOT_FOUND");
        }
        User hospital = userRepository.findById(req.getHospitalId().trim())
                .orElseThrow(() -> new IllegalArgumentException("HOSPITAL_NOT_FOUND"));
        if (!"hospital".equalsIgnoreCase(hospital.getRole())) {
            throw new IllegalArgumentException("NOT_A_HOSPITAL");
        }

        if (req.getPatientName() == null || req.getPatientName().trim().isEmpty()) {
            throw new IllegalArgumentException("Patient name is required");
        }
        String pt = req.getPatientName().trim();
        if (pt.matches("^\\d+$")) {
            throw new IllegalArgumentException("Patient name must contain valid text, not only numbers");
        }

        if (req.getBloodGroup() == null || req.getBloodGroup().trim().isEmpty()) {
            throw new IllegalArgumentException("Blood group is required");
        }
        String bg = req.getBloodGroup().trim();
        if (!VALID_BLOOD_GROUPS.contains(bg)) {
            throw new IllegalArgumentException("Blood group must be one of A+, A-, B+, B-, AB+, AB-, O+, O-");
        }

        if (req.getUnits() == null || req.getUnits() <= 0) {
            throw new IllegalArgumentException("Units required must be an integer greater than 0");
        }

        String reqType = (req.getRequestType() != null && !req.getRequestType().trim().isEmpty())
                ? req.getRequestType().trim().toUpperCase()
                : "EMERGENCY";

        if (!"EMERGENCY".equals(reqType) && !"SCHEDULED".equals(reqType)) {
            throw new IllegalArgumentException("Request type must be EMERGENCY or SCHEDULED");
        }

        String urgency = req.getUrgency() != null ? req.getUrgency().trim() : "";
        if ("EMERGENCY".equals(reqType)) {
            if (urgency.isEmpty()) {
                throw new IllegalArgumentException("Urgency level is required");
            }
        } else {
            if (urgency.isEmpty()) {
                urgency = "Normal";
            }
        }

        // Contact number must automatically come from the hospital's registered account phone number
        String contact = (hospital.getPhone() != null && !hospital.getPhone().trim().isEmpty())
                ? hospital.getPhone().trim()
                : (req.getContact() != null ? req.getContact().trim() : "");

        if (contact.isEmpty()) {
            throw new IllegalArgumentException("Contact phone number is required");
        }
        if (!contact.matches("^[6-9]\\d{9}$")) {
            throw new IllegalArgumentException("Contact number must be a valid 10-digit Indian mobile number starting with 6-9");
        }

        if (req.getRadius() == null || req.getRadius() <= 0 || req.getRadius() > 100) {
            throw new IllegalArgumentException("Search radius must be between 1km and 100km");
        }

        LocalDateTime opTime = null;
        LocalDateTime accDeadline = null;
        String reason = null;

        if ("SCHEDULED".equals(reqType)) {
            if (req.getReason() == null || req.getReason().trim().isEmpty()) {
                throw new IllegalArgumentException("Reason / clinical cause is required for scheduled requests");
            }
            reason = req.getReason().trim();

            if (req.getOperationTime() == null || req.getOperationTime().trim().isEmpty()) {
                throw new IllegalArgumentException("Operation / requirement date and time is required for scheduled requests");
            }
            try {
                opTime = LocalDateTime.parse(req.getOperationTime().trim());
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid operation time format (expected ISO datetime)");
            }

            if (req.getAccumulationDeadline() == null || req.getAccumulationDeadline().trim().isEmpty()) {
                throw new IllegalArgumentException("Accumulation deadline is required for scheduled requests");
            }
            try {
                accDeadline = LocalDateTime.parse(req.getAccumulationDeadline().trim());
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid accumulation deadline format (expected ISO datetime)");
            }

            if (!accDeadline.isAfter(LocalDateTime.now())) {
                throw new IllegalArgumentException("Accumulation deadline must be in the future");
            }

            if (!accDeadline.isBefore(opTime)) {
                throw new IllegalArgumentException("Accumulation deadline must be before the operation / requirement time");
            }
        }

        String requestId = "r" + System.currentTimeMillis();

        BloodRequest br = new BloodRequest();
        br.setId(requestId);
        br.setHospitalId(req.getHospitalId());
        br.setPatientName(pt);
        br.setBloodGroup(bg);
        br.setUnits(req.getUnits());
        br.setSecuredUnits(0);
        br.setUrgency(urgency);
        br.setContact(contact);
        br.setNotes(req.getNotes() != null ? req.getNotes().trim() : null);
        br.setStatus("open");
        br.setRadius(req.getRadius());
        br.setRequestType(reqType);
        br.setReason(reason);
        br.setOperationTime(opTime);
        br.setAccumulationDeadline(accDeadline);
        br.setDonorAlertsSent(false);
        br.setRemainingUnitsAtDonorAlert(null);
        br.setCreatedAt(LocalDateTime.now());
        br.setFulfilledAt(null);

        return bloodRequestRepository.save(br);
    }

    private HospitalRequestDTO convertToDTO(BloodRequest br) {
        boolean isExpired = false;
        if ("SCHEDULED".equalsIgnoreCase(br.getRequestType()) && br.getAccumulationDeadline() != null) {
            if (LocalDateTime.now().isAfter(br.getAccumulationDeadline())) {
                isExpired = true;
                if ("open".equalsIgnoreCase(br.getStatus())) {
                    if (br.getSecuredUnits() != null && br.getSecuredUnits() > 0) {
                        br.setStatus("partially_fulfilled");
                    } else {
                        br.setStatus("expired");
                    }
                    bloodRequestRepository.save(br);
                }
            }
        }

        HospitalRequestDTO dto = new HospitalRequestDTO();
        dto.setId(br.getId());
        dto.setHospitalId(br.getHospitalId());
        dto.setPatientName(br.getPatientName());
        dto.setBloodGroup(br.getBloodGroup());
        dto.setUnits(br.getUnits());
        dto.setSecuredUnits(br.getSecuredUnits() != null ? br.getSecuredUnits() : 0);
        dto.setUrgency(br.getUrgency());
        dto.setContact(br.getContact());
        dto.setNotes(br.getNotes());
        dto.setStatus(br.getStatus());
        dto.setRadius(br.getRadius());
        dto.setRequestType(br.getRequestType());
        dto.setReason(br.getReason());
        dto.setOperationTime(br.getOperationTime() != null ? br.getOperationTime().toString() : null);
        dto.setAccumulationDeadline(br.getAccumulationDeadline() != null ? br.getAccumulationDeadline().toString() : null);
        dto.setIsExpired(isExpired);
        dto.setDonorAlertsSent(br.getDonorAlertsSent() != null ? br.getDonorAlertsSent() : false);
        dto.setRemainingUnitsAtDonorAlert(br.getRemainingUnitsAtDonorAlert());
        dto.setCurrentWave(br.getCurrentWave() != null ? br.getCurrentWave() : 1);
        dto.setWaveStartedAt(br.getWaveStartedAt() != null ? br.getWaveStartedAt().toString() : null);
        dto.setWaveExpiresAt(br.getWaveExpiresAt() != null ? br.getWaveExpiresAt().toString() : null);
        dto.setCreatedAt(br.getCreatedAt() != null ? br.getCreatedAt().toString() : null);
        dto.setFulfilledAt(br.getFulfilledAt() != null ? br.getFulfilledAt().toString() : null);

        User hospital = userRepository.findById(br.getHospitalId()).orElse(null);

        // Populate matched blood banks from MySQL request_blood_banks
        List<RequestBloodBank> matchedBanks = requestBloodBankRepository.findByBloodRequestId(br.getId());
        List<Map<String, Object>> bankList = new ArrayList<>();
        for (RequestBloodBank mb : matchedBanks) {
            Map<String, Object> bm = new HashMap<>();
            bm.put("id", mb.getBloodBankId());
            bm.put("distance", mb.getDistance());
            bm.put("bloodGroup", br.getBloodGroup());
            bm.put("availableUnitsAtMatch", mb.getAvailableUnitsAtMatch());
            bm.put("status", mb.getStatus());
            bm.put("responseStatus", mb.getResponseStatus());
            bm.put("unitsSecured", mb.getUnitsSecured() != null ? mb.getUnitsSecured() : 0);
            bm.put("reservedUnits", mb.getReservedUnits() != null ? mb.getReservedUnits() : 0);
            bm.put("reservedGroupsJson", mb.getReservedGroupsJson());
            bm.put("respondedAt", mb.getRespondedAt() != null ? mb.getRespondedAt().toString() : null);

            if (inventoryRepository != null && mb.getBloodBankId() != null) {
                Inventory inv = inventoryRepository.findByBloodBankId(mb.getBloodBankId()).orElse(null);
                if (inv != null) {
                    Map<String, Integer> compStock = new LinkedHashMap<>();
                    for (String g : BloodBankRequestService.getOrderedCompatibleGroups(br.getBloodGroup())) {
                        int s = getStockForGroup(inv, g);
                        if (s > 0) {
                            compStock.put(g, s);
                        }
                    }
                    bm.put("compatibleStock", compStock);
                }
            }

            User bb = userRepository.findById(mb.getBloodBankId()).orElse(null);
            if (bb != null) {
                bm.put("name", bb.getName());
                bm.put("phone", bb.getPhone());
                bm.put("city", bb.getCity());
                bm.put("state", bb.getState());
                bm.put("registrationNumber", bb.getRegistrationNumber());
            }

            bankList.add(bm);
        }
        dto.setBloodBanks(bankList);

        // Populate donor alert states from MySQL request_donors
        List<RequestDonor> donors = requestDonorRepository.findByBloodRequestId(br.getId());
        List<Map<String, Object>> direct = new ArrayList<>();
        List<Map<String, Object>> waiting = new ArrayList<>();
        for (RequestDonor rd : donors) {
            Map<String, Object> dm = new HashMap<>();
            dm.put("id", rd.getDonorId());
            dm.put("status", rd.getStatus());
            dm.put("type", rd.getAlertType());
            dm.put("at", rd.getRespondedAt() != null ? rd.getRespondedAt().toString() : null);
            dm.put("confirmedAt", rd.getConfirmedAt() != null ? rd.getConfirmedAt().toString() : null);

            User donor = userRepository.findById(rd.getDonorId()).orElse(null);
            if (donor != null) {
                dm.put("name", donor.getName());
                dm.put("bloodGroup", donor.getBloodGroup());
                dm.put("phone", donor.getPhone());
                dm.put("city", donor.getCity());
                dm.put("state", donor.getState());
                dm.put("address", donor.getAddress());
                dm.put("lat", donor.getLat());
                dm.put("lng", donor.getLng());

                if (hospital != null && hospital.getLat() != null && hospital.getLng() != null
                        && donor.getLat() != null && donor.getLng() != null) {
                    Double dist = calculateDistance(hospital.getLat(), hospital.getLng(), donor.getLat(), donor.getLng());
                    if (dist != null) {
                        dm.put("distance", Math.round(dist * 10.0) / 10.0);
                        if ("accepted".equalsIgnoreCase(rd.getStatus())) {
                            dm.put("rankScore", Math.round((dist * 1.5) * 100.0) / 100.0);
                        }
                    }
                }
            }

            if ("direct".equalsIgnoreCase(rd.getAlertType())) {
                direct.add(dm);
            } else {
                waiting.add(dm);
            }
        }

        Comparator<Map<String, Object>> donorMapComparator = (a, b) -> {
            String statusA = (String) a.get("status");
            String statusB = (String) b.get("status");
            boolean acceptedA = "accepted".equalsIgnoreCase(statusA) || "confirmed".equalsIgnoreCase(statusA);
            boolean acceptedB = "accepted".equalsIgnoreCase(statusB) || "confirmed".equalsIgnoreCase(statusB);

            if (acceptedA && acceptedB) {
                Double distA = a.get("distance") != null ? ((Number) a.get("distance")).doubleValue() : 999.0;
                Double distB = b.get("distance") != null ? ((Number) b.get("distance")).doubleValue() : 999.0;
                return Double.compare(distA, distB);
            } else if (acceptedA) {
                return -1;
            } else if (acceptedB) {
                return 1;
            }
            return 0;
        };

        direct.sort(donorMapComparator);
        waiting.sort(donorMapComparator);

        dto.setDirectDonors(direct);
        dto.setWaitingDonors(waiting);

        return dto;
    }

    @Transactional
    public List<HospitalRequestDTO> getHospitalRequests(String hospitalId) {
        if (hospitalId == null || hospitalId.trim().isEmpty()) {
            throw new IllegalArgumentException("HOSPITAL_NOT_FOUND");
        }

        User hospital = userRepository.findById(hospitalId)
                .orElseThrow(() -> new IllegalArgumentException("HOSPITAL_NOT_FOUND"));

        if (!"hospital".equalsIgnoreCase(hospital.getRole())) {
            throw new IllegalArgumentException("NOT_A_HOSPITAL");
        }

        List<BloodRequest> rawRequests = bloodRequestRepository.findByHospitalIdOrderByCreatedAtDesc(hospitalId);
        List<HospitalRequestDTO> dtoList = new ArrayList<>();

        for (BloodRequest br : rawRequests) {
            dtoList.add(convertToDTO(br));
        }

        return dtoList;
    }

    @Transactional
    public HospitalRequestDTO getRequestById(String requestId) {
        if (requestId == null || requestId.trim().isEmpty()) {
            throw new IllegalArgumentException("REQUEST_NOT_FOUND");
        }

        BloodRequest br = bloodRequestRepository.findById(requestId.trim())
                .orElseThrow(() -> new IllegalArgumentException("REQUEST_NOT_FOUND"));

        return convertToDTO(br);
    }

    @Transactional(readOnly = true)
    public long getTotalRequestCount() {
        return bloodRequestRepository.count();
    }

    public static int calculateWaveSize(int remainingRequiredUnits) {
        int remaining = Math.max(1, remainingRequiredUnits);
        return Math.max(5, remaining * 2);
    }

    public static int getResponseWindowMinutes(String urgency) {
        if (urgency != null && (urgency.equalsIgnoreCase("critical") || urgency.equalsIgnoreCase("urgent"))) {
            return 5;
        }
        return 15;
    }

    @Transactional
    public List<RequestDonor> createDonorAlerts(String requestId, CreateDonorAlertsRequest req) {
        if (requestId == null || requestId.trim().isEmpty() || req == null || req.getDonors() == null || req.getDonors().isEmpty()) {
            throw new IllegalArgumentException("MISSING_ALERT_DATA");
        }

        BloodRequest br = bloodRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("REQUEST_NOT_FOUND"));

        br.setDonorAlertsSent(true);
        if (req.getRemainingUnitsAtDonorAlert() != null) {
            br.setRemainingUnitsAtDonorAlert(req.getRemainingUnitsAtDonorAlert());
        }

        int remaining = (req.getRemainingUnitsAtDonorAlert() != null && req.getRemainingUnitsAtDonorAlert() > 0)
                ? req.getRemainingUnitsAtDonorAlert()
                : Math.max(1, (br.getUnits() != null ? br.getUnits() : 1) - (br.getSecuredUnits() != null ? br.getSecuredUnits() : 0));
        int waveSize = calculateWaveSize(remaining);
        int windowMinutes = getResponseWindowMinutes(br.getUrgency());

        LocalDateTime now = LocalDateTime.now();
        br.setCurrentWave(1);
        br.setWaveStartedAt(now);
        br.setWaveExpiresAt(now.plusMinutes(windowMinutes));

        List<RequestDonor> createdAlerts = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (CreateDonorAlertsRequest.DonorAlertItem item : req.getDonors()) {
            if (createdAlerts.size() >= waveSize) {
                break;
            }

            if (item.getDonorId() == null || item.getDonorId().trim().isEmpty()) {
                continue;
            }

            User donor = userRepository.findById(item.getDonorId()).orElse(null);
            if (donor == null || !"donor".equalsIgnoreCase(donor.getRole())) {
                continue;
            }

            // Exclude suspended donors
            if (Boolean.TRUE.equals(donor.getSuspended())) {
                continue;
            }

            // Exclude ineligible donors in 90-day relaxation period
            if (!isDonorEligible(donor, today)) {
                continue;
            }

            Optional<RequestDonor> existingOpt = requestDonorRepository.findByDonorIdAndBloodRequestId(item.getDonorId(), requestId);
            RequestDonor rd;
            if (existingOpt.isPresent()) {
                rd = existingOpt.get();
                if (!"unalerted".equalsIgnoreCase(rd.getStatus()) && !"matched".equalsIgnoreCase(rd.getAlertType())) {
                    continue; // Already alerted
                }
            } else {
                rd = new RequestDonor();
                rd.setBloodRequest(br);
                rd.setDonorId(item.getDonorId());
            }

            rd.setAlertType(item.getAlertType() != null ? item.getAlertType().toLowerCase() : "direct");
            rd.setStatus("pending");
            rd.setRespondedAt(null);

            createdAlerts.add(requestDonorRepository.save(rd));

            // Start 2-hour notification cooldown by updating donor's lastNotifiedAt
            donor.setLastNotifiedAt(now);
            userRepository.save(donor);
        }

        bloodRequestRepository.save(br);
        return createdAlerts;
    }

    @Transactional
    public List<RequestDonor> progressDonorAlertWavesForRequest(String requestId) {
        if (requestId == null || requestId.trim().isEmpty()) {
            return Collections.emptyList();
        }
        BloodRequest br = bloodRequestRepository.findById(requestId.trim()).orElse(null);
        if (br == null) {
            return Collections.emptyList();
        }

        // Check if request is active and donor alerts were sent
        if (!Boolean.TRUE.equals(br.getDonorAlertsSent())) {
            return Collections.emptyList();
        }
        String status = br.getStatus() != null ? br.getStatus().toLowerCase() : "";
        if ("fulfilled".equals(status) || "cancelled".equals(status) || "closed".equals(status)) {
            return Collections.emptyList();
        }

        int totalUnits = br.getUnits() != null ? br.getUnits() : 0;
        int securedUnits = br.getSecuredUnits() != null ? br.getSecuredUnits() : 0;
        if (securedUnits >= totalUnits) {
            return Collections.emptyList();
        }

        LocalDateTime now = LocalDateTime.now();
        // Check if current wave has expired
        if (br.getWaveExpiresAt() != null && now.isBefore(br.getWaveExpiresAt())) {
            return Collections.emptyList(); // Wave has not expired yet
        }

        int remainingUnits = Math.max(1, totalUnits - securedUnits);
        int nextWaveSize = calculateWaveSize(remainingUnits);
        int windowMinutes = getResponseWindowMinutes(br.getUrgency());

        // Origin hospital coordinates
        User hospital = br.getHospitalId() != null ? userRepository.findById(br.getHospitalId()).orElse(null) : null;
        Double hLat = hospital != null ? hospital.getLat() : null;
        Double hLng = hospital != null ? hospital.getLng() : null;

        int initialRadius = (br.getRadius() != null && br.getRadius() > 0) ? br.getRadius() : 15;
        String bg = br.getBloodGroup();

        // Already alerted donors for this request
        List<RequestDonor> existingRequestDonors = requestDonorRepository.findByBloodRequestId(br.getId());
        Set<String> alreadyAlertedDonorIds = new HashSet<>();
        Map<String, RequestDonor> existingRdMap = new HashMap<>();
        for (RequestDonor rd : existingRequestDonors) {
            if (rd.getDonorId() != null) {
                existingRdMap.put(rd.getDonorId(), rd);
                if (!"unalerted".equalsIgnoreCase(rd.getStatus()) && !"matched".equalsIgnoreCase(rd.getAlertType())) {
                    alreadyAlertedDonorIds.add(rd.getDonorId());
                }
            }
        }

        // Fetch all active available donors
        List<User> availableDonors = userRepository.findByRoleIgnoreCaseAndAvailableTrue("donor");
        LocalDate today = LocalDate.now();
        Set<String> compatibleGroups = COMPAT_MAP.getOrDefault(bg, Collections.singleton(bg));

        List<User> candidatePool = new ArrayList<>();
        for (User donor : availableDonors) {
            if (donor.getId() == null || alreadyAlertedDonorIds.contains(donor.getId())) {
                continue; // Prevent duplicate alerts
            }
            if (Boolean.TRUE.equals(donor.getSuspended())) {
                continue; // Exclude suspended
            }
            if (!isDonorEligible(donor, today)) {
                continue; // Exclude ineligible (90-day)
            }
            if (donor.getBloodGroup() != null && compatibleGroups.contains(donor.getBloodGroup())) {
                candidatePool.add(donor);
            }
        }

        class CandidateDonor {
            final User user;
            final Double distance;
            final String freshnessStatus;
            final double rankScore;

            CandidateDonor(User user, Double distance, String freshnessStatus, double rankScore) {
                this.user = user;
                this.distance = distance;
                this.freshnessStatus = freshnessStatus;
                this.rankScore = rankScore;
            }
        }

        List<CandidateDonor> allCandidates = new ArrayList<>();
        for (User d : candidatePool) {
            ResolvedDonorLocation loc = resolveDonorLocation(d, now);
            if (loc == null) {
                continue;
            }
            Double dist = calculateDistance(hLat, hLng, loc.getLat(), loc.getLng());
            double rankScore = calculateDonorRankScore(dist, loc.getFreshnessPenalty(), loc.getSourcePenalty(), d);
            allCandidates.add(new CandidateDonor(d, dist, loc.getFreshnessStatus(), rankScore));
        }

        Comparator<CandidateDonor> donorComparator = (a, b) -> {
            if (Double.compare(a.rankScore, b.rankScore) != 0) {
                return Double.compare(a.rankScore, b.rankScore);
            }
            double distA = a.distance != null ? a.distance : 999.0;
            double distB = b.distance != null ? b.distance : 999.0;
            return Double.compare(distA, distB);
        };

        List<CandidateDonor> matchedList = new ArrayList<>();
        if (hLat != null && hLng != null) {
            for (CandidateDonor c : allCandidates) {
                if (c.distance == null || c.distance <= initialRadius) {
                    matchedList.add(c);
                }
            }
            matchedList.sort(donorComparator);
        } else {
            matchedList.addAll(allCandidates);
            matchedList.sort(donorComparator);
        }

        List<RequestDonor> nextWaveAlerts = new ArrayList<>();
        int toAlertCount = Math.min(nextWaveSize, matchedList.size());
        for (int i = 0; i < toAlertCount; i++) {
            CandidateDonor c = matchedList.get(i);
            RequestDonor rd = existingRdMap.get(c.user.getId());
            if (rd == null) {
                rd = new RequestDonor();
                rd.setBloodRequest(br);
                rd.setDonorId(c.user.getId());
            }
            rd.setAlertType("direct");
            rd.setStatus("pending");
            rd.setRespondedAt(null);
            nextWaveAlerts.add(requestDonorRepository.save(rd));

            c.user.setLastNotifiedAt(now);
            userRepository.save(c.user);
        }

        // Increment wave and reset timers
        int currentWaveNum = br.getCurrentWave() != null ? br.getCurrentWave() : 1;
        br.setCurrentWave(currentWaveNum + 1);
        br.setWaveStartedAt(now);
        br.setWaveExpiresAt(now.plusMinutes(windowMinutes));
        bloodRequestRepository.save(br);

        return nextWaveAlerts;
    }

    @Scheduled(fixedDelay = 5000, initialDelay = 1000)
    @Transactional
    public void processExpiredWaves() {
        LocalDateTime now = LocalDateTime.now();
        List<BloodRequest> activeRequests = bloodRequestRepository.findAll();
        for (BloodRequest br : activeRequests) {
            if (Boolean.TRUE.equals(br.getDonorAlertsSent())) {
                String status = br.getStatus() != null ? br.getStatus().toLowerCase() : "";
                if (!"fulfilled".equals(status) && !"cancelled".equals(status) && !"closed".equals(status)) {
                    int totalUnits = br.getUnits() != null ? br.getUnits() : 0;
                    int securedUnits = br.getSecuredUnits() != null ? br.getSecuredUnits() : 0;
                    if (securedUnits < totalUnits && br.getWaveExpiresAt() != null && !now.isBefore(br.getWaveExpiresAt())) {
                        try {
                            progressDonorAlertWavesForRequest(br.getId());
                        } catch (Exception ignored) {}
                    }
                }
            }
        }
    }

    @Transactional
    public BloodRequest fulfillRequest(String requestId, String hospitalId) {
        if (requestId == null || requestId.trim().isEmpty()) {
            throw new IllegalArgumentException("REQUEST_NOT_FOUND");
        }

        BloodRequest br = bloodRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("REQUEST_NOT_FOUND"));

        if (hospitalId != null && !hospitalId.trim().isEmpty() && !hospitalId.equals(br.getHospitalId())) {
            throw new IllegalArgumentException("UNAUTHORIZED");
        }

        br.setStatus("fulfilled");
        br.setSecuredUnits(br.getUnits());
        br.setFulfilledAt(LocalDateTime.now());

        // Close pending donor alerts
        List<RequestDonor> donors = requestDonorRepository.findByBloodRequestId(requestId);
        for (RequestDonor d : donors) {
            if ("pending".equalsIgnoreCase(d.getStatus())) {
                d.setStatus("closed");
                requestDonorRepository.save(d);
            }
        }

        // Close waiting blood banks
        List<RequestBloodBank> banks = requestBloodBankRepository.findByBloodRequestId(requestId);
        for (RequestBloodBank b : banks) {
            if ("waiting".equalsIgnoreCase(b.getResponseStatus()) || "pending".equalsIgnoreCase(b.getResponseStatus())) {
                b.setResponseStatus("closed");
                b.setUnavailable(true);
                requestBloodBankRepository.save(b);
            }
        }

        return bloodRequestRepository.save(br);
    }

    @Transactional
    public BloodRequest confirmDonor(String requestId, String donorId, String hospitalId) {
        if (requestId == null || donorId == null) {
            throw new IllegalArgumentException("REQUEST_NOT_FOUND");
        }

        BloodRequest br = bloodRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("REQUEST_NOT_FOUND"));

        if (hospitalId != null && !hospitalId.trim().isEmpty() && !hospitalId.equals(br.getHospitalId())) {
            throw new IllegalArgumentException("UNAUTHORIZED");
        }

        if ("SCHEDULED".equalsIgnoreCase(br.getRequestType()) && br.getAccumulationDeadline() != null) {
            if (LocalDateTime.now().isAfter(br.getAccumulationDeadline())) {
                throw new IllegalArgumentException("ACCUMULATION_DEADLINE_PASSED");
            }
        }

        RequestDonor rd = requestDonorRepository.findByDonorIdAndBloodRequestId(donorId, requestId)
                .orElseThrow(() -> new IllegalArgumentException("ALERT_NOT_FOUND"));

        if ("confirmed".equalsIgnoreCase(rd.getStatus())) {
            return br;
        }

        if (!"accepted".equalsIgnoreCase(rd.getStatus())) {
            throw new IllegalArgumentException("DONOR_NOT_ACCEPTED");
        }

        if ("fulfilled".equalsIgnoreCase(br.getStatus()) || (br.getSecuredUnits() != null && br.getSecuredUnits() >= br.getUnits())) {
            throw new IllegalArgumentException("REQUEST_ALREADY_FULFILLED");
        }

        rd.setStatus("confirmed");
        rd.setConfirmedAt(LocalDateTime.now());
        requestDonorRepository.save(rd);

        // Update donor last donation date and next eligible date (+90 days) in MySQL users table
        User donor = userRepository.findById(donorId).orElse(null);
        LocalDate today = LocalDate.now();
        String donationDate = today.toString();
        String nextEligible = today.plusDays(90).toString();

        if (donor != null) {
            donor.setLastDonation(donationDate);
            donor.setNextEligibleDate(nextEligible);
            userRepository.save(donor);

            // Record exactly one donation in MySQL donation_history
            User hospital = br.getHospitalId() != null ? userRepository.findById(br.getHospitalId()).orElse(null) : null;
            String location = "Hospital Donation";
            if (hospital != null && hospital.getName() != null && !hospital.getName().trim().isEmpty()) {
                location = hospital.getName().trim();
                if (hospital.getCity() != null && !hospital.getCity().trim().isEmpty()) {
                    location += ", " + hospital.getCity().trim();
                }
            }

            DonationHistory dh = new DonationHistory();
            dh.setId("dh_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
            dh.setDonorId(donor.getId());
            dh.setDate(donationDate);
            dh.setLocation(location);
            dh.setUnits(1);
            dh.setNotes("Donation for Blood Request #" + br.getId());
            dh.setCreatedAt(LocalDateTime.now());
            donationHistoryRepository.save(dh);
        }

        // Recalculate secured units
        int bankUnits = requestBloodBankRepository.findByBloodRequestId(requestId).stream()
                .filter(b -> "confirmed".equalsIgnoreCase(b.getResponseStatus()))
                .mapToInt(b -> b.getUnitsSecured() != null ? b.getUnitsSecured() : 0)
                .sum();

        int donorUnits = (int) requestDonorRepository.findByBloodRequestId(requestId).stream()
                .filter(d -> "confirmed".equalsIgnoreCase(d.getStatus()))
                .count();

        int totalSecured = bankUnits + donorUnits;
        br.setSecuredUnits(totalSecured);

        if (totalSecured >= br.getUnits()) {
            br.setStatus("fulfilled");
            br.setFulfilledAt(LocalDateTime.now());
            // Close remaining pending alerts
            List<RequestDonor> allDonors = requestDonorRepository.findByBloodRequestId(requestId);
            for (RequestDonor d : allDonors) {
                if ("pending".equalsIgnoreCase(d.getStatus())) {
                    d.setStatus("closed");
                    requestDonorRepository.save(d);
                }
            }
            // Close remaining blood banks
            List<RequestBloodBank> allBanks = requestBloodBankRepository.findByBloodRequestId(requestId);
            for (RequestBloodBank b : allBanks) {
                if ("waiting".equalsIgnoreCase(b.getResponseStatus()) || "pending".equalsIgnoreCase(b.getResponseStatus())) {
                    b.setResponseStatus("closed");
                    b.setUnavailable(true);
                    requestBloodBankRepository.save(b);
                }
            }
        } else if (totalSecured > 0) {
            br.setStatus("partially_fulfilled");
        }

        return bloodRequestRepository.save(br);
    }

    private static final Map<String, Set<String>> COMPAT_MAP = new HashMap<>();
    static {
        COMPAT_MAP.put("O-", new HashSet<>(Arrays.asList("O-")));
        COMPAT_MAP.put("O+", new HashSet<>(Arrays.asList("O-", "O+")));
        COMPAT_MAP.put("A-", new HashSet<>(Arrays.asList("O-", "A-")));
        COMPAT_MAP.put("A+", new HashSet<>(Arrays.asList("O-", "O+", "A-", "A+")));
        COMPAT_MAP.put("B-", new HashSet<>(Arrays.asList("O-", "B-")));
        COMPAT_MAP.put("B+", new HashSet<>(Arrays.asList("O-", "O+", "B-", "B+")));
        COMPAT_MAP.put("AB-", new HashSet<>(Arrays.asList("O-", "A-", "B-", "AB-")));
        COMPAT_MAP.put("AB+", new HashSet<>(Arrays.asList("O-", "O+", "A-", "A+", "B-", "B+", "AB-", "AB+")));
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

    public static final long NOTIFICATION_COOLDOWN_HOURS = 2L;
    public static final double COOLDOWN_PENALTY = 15.0;
    public static final double SOURCE_PENALTY_PROFILE = 15.0;

    public static class ResolvedDonorLocation {
        private final Double lat;
        private final Double lng;
        private final String freshnessStatus;
        private final String source;
        private final double freshnessPenalty;
        private final double sourcePenalty;

        public ResolvedDonorLocation(Double lat, Double lng, String freshnessStatus, String source, double freshnessPenalty, double sourcePenalty) {
            this.lat = lat;
            this.lng = lng;
            this.freshnessStatus = freshnessStatus;
            this.source = source;
            this.freshnessPenalty = freshnessPenalty;
            this.sourcePenalty = sourcePenalty;
        }

        public Double getLat() { return lat; }
        public Double getLng() { return lng; }
        public String getFreshnessStatus() { return freshnessStatus; }
        public String getSource() { return source; }
        public double getFreshnessPenalty() { return freshnessPenalty; }
        public double getSourcePenalty() { return sourcePenalty; }
    }

    public static boolean isValidCoordinate(Double lat, Double lng) {
        return lat != null && lng != null &&
                lat >= -90.0 && lat <= 90.0 &&
                lng >= -180.0 && lng <= 180.0;
    }

    public static ResolvedDonorLocation resolveDonorLocation(User donor) {
        return resolveDonorLocation(donor, LocalDateTime.now());
    }

    public static ResolvedDonorLocation resolveDonorLocation(User donor, LocalDateTime now) {
        if (donor == null) return null;
        if (now == null) now = LocalDateTime.now();

        LocalDateTime locUpdated = donor.getLocationUpdatedAt();
        Double gpsLat = donor.getLat();
        Double gpsLng = donor.getLng();
        boolean hasValidGps = isValidCoordinate(gpsLat, gpsLng);

        Double profLat = donor.getRegisteredLat();
        Double profLng = donor.getRegisteredLng();
        boolean hasValidProfile = isValidCoordinate(profLat, profLng);

        if (hasValidGps && locUpdated != null) {
            java.time.Duration duration = java.time.Duration.between(locUpdated, now);
            long seconds = duration.getSeconds();
            if (seconds < 0) {
                seconds = 0;
            }

            if (seconds < 2 * 3600) { // < 2h: Fresh (0 freshness penalty, 0 source penalty)
                return new ResolvedDonorLocation(gpsLat, gpsLng, "fresh", "GPS", 0.0, 0.0);
            } else if (seconds < 24 * 3600) { // >= 2h and < 24h: Aging (12 freshness penalty, 0 source penalty)
                return new ResolvedDonorLocation(gpsLat, gpsLng, "aging", "GPS", 12.0, 0.0);
            } else if (seconds <= 72 * 3600) { // >= 24h and <= 72h: Stale (28 freshness penalty, 0 source penalty)
                return new ResolvedDonorLocation(gpsLat, gpsLng, "stale", "GPS", 28.0, 0.0);
            } else { // > 72h: GPS discarded, fall back to profile location (0 freshness penalty, 15 source penalty)
                if (hasValidProfile) {
                    return new ResolvedDonorLocation(profLat, profLng, "profile", "PROFILE", 0.0, SOURCE_PENALTY_PROFILE);
                } else {
                    return null; // GPS discarded, no profile coordinates -> excluded
                }
            }
        } else {
            // No valid GPS or locUpdated is null -> fall back to profile location (0 freshness penalty, 15 source penalty)
            if (hasValidProfile) {
                return new ResolvedDonorLocation(profLat, profLng, "profile", "PROFILE", 0.0, SOURCE_PENALTY_PROFILE);
            } else {
                return null; // No usable location -> excluded
            }
        }
    }

    public static String calculateFreshnessStatus(User donor) {
        ResolvedDonorLocation res = resolveDonorLocation(donor, LocalDateTime.now());
        return res != null ? res.getFreshnessStatus() : "unknown";
    }

    public static String calculateFreshnessStatus(User donor, LocalDateTime now) {
        ResolvedDonorLocation res = resolveDonorLocation(donor, now);
        return res != null ? res.getFreshnessStatus() : "unknown";
    }

    public static double calculateDonorRankScore(Double distance, String freshnessStatus) {
        return calculateDonorRankScore(distance, freshnessStatus, null, null);
    }

    public static double calculateDonorRankScore(Double distance, String freshnessStatus, User donor) {
        return calculateDonorRankScore(distance, freshnessStatus, donor, null);
    }

    public static double calculateDonorRankScore(Double distance, String freshnessStatus, User donor, String source) {
        double distPenalty = (distance != null) ? distance * 1.5 : 999.0;
        double freshnessPenalty = 0.0;
        if ("aging".equalsIgnoreCase(freshnessStatus)) {
            freshnessPenalty = 12.0;
        } else if ("stale".equalsIgnoreCase(freshnessStatus)) {
            freshnessPenalty = 28.0;
        }

        double sourcePenalty = 0.0;
        if ("PROFILE".equalsIgnoreCase(source) || "profile".equalsIgnoreCase(freshnessStatus)) {
            sourcePenalty = SOURCE_PENALTY_PROFILE;
        }

        double cooldownPenalty = 0.0;
        if (donor != null && donor.getLastNotifiedAt() != null) {
            long minutesSinceNotified = java.time.Duration.between(donor.getLastNotifiedAt(), LocalDateTime.now()).toMinutes();
            if (minutesSinceNotified >= 0 && minutesSinceNotified < NOTIFICATION_COOLDOWN_HOURS * 60) {
                cooldownPenalty = COOLDOWN_PENALTY;
            }
        }

        return distPenalty + freshnessPenalty + sourcePenalty + cooldownPenalty;
    }

    public static double calculateDonorRankScore(Double distance, double freshnessPenalty, User donor) {
        return calculateDonorRankScore(distance, freshnessPenalty, 0.0, donor);
    }

    public static double calculateDonorRankScore(Double distance, double freshnessPenalty, double sourcePenalty, User donor) {
        double distPenalty = (distance != null) ? distance * 1.5 : 999.0;
        double cooldownPenalty = 0.0;
        if (donor != null && donor.getLastNotifiedAt() != null) {
            long minutesSinceNotified = java.time.Duration.between(donor.getLastNotifiedAt(), LocalDateTime.now()).toMinutes();
            if (minutesSinceNotified >= 0 && minutesSinceNotified < NOTIFICATION_COOLDOWN_HOURS * 60) {
                cooldownPenalty = COOLDOWN_PENALTY;
            }
        }

        return distPenalty + freshnessPenalty + sourcePenalty + cooldownPenalty;
    }

    public static boolean isDonorEligible(User donor, LocalDate today) {
        if (donor == null) return false;
        LocalDate checkDate = today != null ? today : LocalDate.now();
        if (donor.getNextEligibleDate() != null && !donor.getNextEligibleDate().trim().isEmpty()) {
            try {
                LocalDate next = LocalDate.parse(donor.getNextEligibleDate().trim());
                if (checkDate.isBefore(next)) {
                    return false;
                }
            } catch (Exception ignored) {}
        } else if (donor.getLastDonation() != null && !donor.getLastDonation().trim().isEmpty()) {
            try {
                LocalDate last = LocalDate.parse(donor.getLastDonation().trim());
                LocalDate next = last.plusDays(90);
                if (checkDate.isBefore(next)) {
                    return false;
                }
            } catch (Exception ignored) {}
        }
        return true;
    }

    @Transactional(readOnly = true)
    public MatchDonorsResponseDTO matchDonorsForRequest(String requestId) {
        BloodRequest br = bloodRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("REQUEST_NOT_FOUND"));

        String bg = br.getBloodGroup();
        int initialRadius = (br.getRadius() != null && br.getRadius() > 0) ? br.getRadius() : 15;

        // Origin hospital coordinates
        User hospital = br.getHospitalId() != null ? userRepository.findById(br.getHospitalId()).orElse(null) : null;
        Double hLat = hospital != null ? hospital.getLat() : null;
        Double hLng = hospital != null ? hospital.getLng() : null;

        // Fetch all active available donors from MySQL via repository query
        List<User> availableDonors = userRepository.findByRoleIgnoreCaseAndAvailableTrue("donor");

        // Filter for donation eligibility (90-day relaxation period) & biological compatibility
        LocalDate today = LocalDate.now();
        Set<String> compatibleGroups = COMPAT_MAP.getOrDefault(bg, Collections.singleton(bg));
        List<User> compatibleDonors = new ArrayList<>();
        for (User donor : availableDonors) {
            // Exclude suspended donors
            if (Boolean.TRUE.equals(donor.getSuspended())) {
                continue;
            }
            // Check 90-day donation relaxation period
            if (!isDonorEligible(donor, today)) {
                continue;
            }
            if (donor.getBloodGroup() != null && compatibleGroups.contains(donor.getBloodGroup())) {
                compatibleDonors.add(donor);
            }
        }

        class CandidateDonor {
            final User user;
            final Double distance;
            final String freshnessStatus;
            final double rankScore;

            CandidateDonor(User user, Double distance, String freshnessStatus, double rankScore) {
                this.user = user;
                this.distance = distance;
                this.freshnessStatus = freshnessStatus;
                this.rankScore = rankScore;
            }
        }

        LocalDateTime now = LocalDateTime.now();
        List<CandidateDonor> allCandidates = new ArrayList<>();
        for (User d : compatibleDonors) {
            ResolvedDonorLocation loc = resolveDonorLocation(d, now);
            if (loc == null) {
                // Exclude donor when GPS is discarded/missing and no profile location exists
                continue;
            }

            Double dist = calculateDistance(hLat, hLng, loc.getLat(), loc.getLng());
            double rankScore = calculateDonorRankScore(dist, loc.getFreshnessPenalty(), loc.getSourcePenalty(), d);
            allCandidates.add(new CandidateDonor(d, dist, loc.getFreshnessStatus(), rankScore));
        }

        Comparator<CandidateDonor> donorComparator = (a, b) -> {
            if (Double.compare(a.rankScore, b.rankScore) != 0) {
                return Double.compare(a.rankScore, b.rankScore);
            }
            double distA = a.distance != null ? a.distance : 999.0;
            double distB = b.distance != null ? b.distance : 999.0;
            return Double.compare(distA, distB);
        };

        List<CandidateDonor> matchedList = new ArrayList<>();
        int radiusUsed = initialRadius;
        boolean expanded = false;

        if (hLat != null && hLng != null) {
            for (CandidateDonor c : allCandidates) {
                if (c.distance == null || c.distance <= initialRadius) {
                    matchedList.add(c);
                }
            }
            matchedList.sort(donorComparator);
        } else {
            matchedList.addAll(allCandidates);
            matchedList.sort(donorComparator);
        }

        List<MatchedDonorItemDTO> direct = new ArrayList<>();
        List<MatchedDonorItemDTO> waiting = new ArrayList<>();

        int totalSize = matchedList.size();
        int directLimit = Math.min(3, totalSize);
        int waitingLimit = Math.min(10, totalSize);

        for (int i = 0; i < directLimit; i++) {
            CandidateDonor c = matchedList.get(i);
            direct.add(new MatchedDonorItemDTO(
                    c.user.getId(),
                    c.user.getName(),
                    c.user.getBloodGroup(),
                    c.user.getPhone(),
                    c.user.getCity(),
                    c.user.getState(),
                    c.distance != null ? Math.round(c.distance * 10.0) / 10.0 : null,
                    c.rankScore,
                    c.freshnessStatus,
                    "pending",
                    "direct",
                    null
            ));
        }

        for (int i = directLimit; i < waitingLimit; i++) {
            CandidateDonor c = matchedList.get(i);
            waiting.add(new MatchedDonorItemDTO(
                    c.user.getId(),
                    c.user.getName(),
                    c.user.getBloodGroup(),
                    c.user.getPhone(),
                    c.user.getCity(),
                    c.user.getState(),
                    c.distance != null ? Math.round(c.distance * 10.0) / 10.0 : null,
                    c.rankScore,
                    c.freshnessStatus,
                    "pending",
                    "waiting",
                    null
            ));
        }

        return new MatchDonorsResponseDTO(direct, waiting, radiusUsed, expanded, totalSize);
    }

    @Transactional
    public Map<String, Object> increaseRequestRadius(String requestId) {
        if (requestId == null || requestId.trim().isEmpty()) {
            throw new IllegalArgumentException("REQUEST_NOT_FOUND");
        }

        BloodRequest br = bloodRequestRepository.findById(requestId.trim())
                .orElseThrow(() -> new IllegalArgumentException("REQUEST_NOT_FOUND"));

        String status = br.getStatus() != null ? br.getStatus().trim().toLowerCase() : "";
        if ("fulfilled".equals(status) || "expired".equals(status) || "closed".equals(status) || "cancelled".equals(status)) {
            throw new IllegalArgumentException("REQUEST_INACTIVE");
        }

        int currentRadius = (br.getRadius() != null && br.getRadius() > 0) ? br.getRadius() : 15;
        int nextRadius;
        if (currentRadius < 2) nextRadius = 2;
        else if (currentRadius < 5) nextRadius = 5;
        else if (currentRadius < 10) nextRadius = 10;
        else if (currentRadius < 15) nextRadius = 15;
        else if (currentRadius < 25) nextRadius = 25;
        else if (currentRadius < 50) nextRadius = 50;
        else {
            throw new IllegalArgumentException("MAX_RADIUS_REACHED");
        }

        br.setRadius(nextRadius);
        br = bloodRequestRepository.save(br);

        // 1. Expand Blood Bank Matches (auto-alert newly discovered banks, preserving existing ones)
        int newBloodBanksCount = bloodBankRequestService.expandBloodBankMatches(br, nextRadius);

        // 2. Perform Full Candidate Search for Donors <= nextRadius
        User hospital = br.getHospitalId() != null ? userRepository.findById(br.getHospitalId()).orElse(null) : null;
        Double hLat = hospital != null ? hospital.getLat() : null;
        Double hLng = hospital != null ? hospital.getLng() : null;

        List<User> availableDonors = userRepository.findByRoleIgnoreCaseAndAvailableTrue("donor");
        LocalDate today = LocalDate.now();
        Set<String> compatibleGroups = COMPAT_MAP.getOrDefault(br.getBloodGroup(), Collections.singleton(br.getBloodGroup()));

        List<RequestDonor> existingDonors = requestDonorRepository.findByBloodRequestId(br.getId());
        Set<String> existingDonorIds = new HashSet<>();
        for (RequestDonor rd : existingDonors) {
            if (rd.getDonorId() != null) {
                existingDonorIds.add(rd.getDonorId());
            }
        }

        LocalDateTime now = LocalDateTime.now();
        int newDonorsCount = 0;

        for (User donor : availableDonors) {
            if (donor.getId() == null || existingDonorIds.contains(donor.getId())) {
                continue; // Already associated with request
            }
            if (Boolean.TRUE.equals(donor.getSuspended())) {
                continue;
            }
            if (!isDonorEligible(donor, today)) {
                continue;
            }
            if (donor.getBloodGroup() == null || !compatibleGroups.contains(donor.getBloodGroup())) {
                continue;
            }

            ResolvedDonorLocation loc = resolveDonorLocation(donor, now);
            if (loc == null) {
                continue;
            }

            Double dist = calculateDistance(hLat, hLng, loc.getLat(), loc.getLng());
            if (dist != null && dist <= nextRadius) {
                // Newly discovered donor: add as matched/unalerted (NO notification sent)
                RequestDonor rd = new RequestDonor();
                rd.setBloodRequest(br);
                rd.setDonorId(donor.getId());
                rd.setAlertType("matched");
                rd.setStatus("unalerted");
                rd.setRespondedAt(null);
                requestDonorRepository.save(rd);

                existingDonorIds.add(donor.getId());
                newDonorsCount++;
            }
        }

        HospitalRequestDTO dto = convertToDTO(br);

        Map<String, Object> result = new HashMap<>();
        result.put("request", dto);
        result.put("newRadius", nextRadius);
        result.put("newBloodBanksCount", newBloodBanksCount);
        result.put("newDonorsCount", newDonorsCount);

        return result;
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
}