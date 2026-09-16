package com.bloodlink.service;

import com.bloodlink.dto.AlertResponseRequest;
import com.bloodlink.dto.DonorAlertDTO;
import com.bloodlink.dto.DonorSearchItemDTO;
import com.bloodlink.dto.LocationUpdateRequest;
import com.bloodlink.entity.BloodRequest;
import com.bloodlink.entity.RequestBloodBank;
import com.bloodlink.entity.RequestDonor;
import com.bloodlink.entity.User;
import com.bloodlink.repository.BloodRequestRepository;
import com.bloodlink.repository.RequestBloodBankRepository;
import com.bloodlink.repository.RequestDonorRepository;
import com.bloodlink.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class DonorService {

    private final UserRepository userRepository;
    private final RequestDonorRepository requestDonorRepository;
    private final RequestBloodBankRepository requestBloodBankRepository;
    private final BloodRequestRepository bloodRequestRepository;

    @Autowired
    public DonorService(UserRepository userRepository,
                        RequestDonorRepository requestDonorRepository,
                        RequestBloodBankRepository requestBloodBankRepository,
                        BloodRequestRepository bloodRequestRepository) {
        this.userRepository = userRepository;
        this.requestDonorRepository = requestDonorRepository;
        this.requestBloodBankRepository = requestBloodBankRepository;
        this.bloodRequestRepository = bloodRequestRepository;
    }

    @Transactional
    public Map<String, Object> updateAvailability(String donorId, Boolean available) {
        if (donorId == null || available == null) {
            throw new IllegalArgumentException("MISSING_DATA");
        }

        User user = userRepository.findById(donorId)
                .orElseThrow(() -> new IllegalArgumentException("DONOR_NOT_FOUND"));

        if (!"donor".equalsIgnoreCase(user.getRole())) {
            throw new IllegalArgumentException("NOT_A_DONOR");
        }

        user.setAvailable(available);
        userRepository.save(user);

        Map<String, Object> response = new HashMap<>();
        response.put("id", donorId);
        response.put("available", user.getAvailable());
        return response;
    }

    @Transactional
    public Map<String, Object> updateLocation(String donorId, LocationUpdateRequest request) {
        if (donorId == null || request == null) {
            throw new IllegalArgumentException("MISSING_LOCATION_DATA");
        }

        Double lat = request.getLat();
        Double lng = request.getLng();
        if (lat == null || lng == null || lat.isNaN() || lat.isInfinite() || lng.isNaN() || lng.isInfinite()
                || lat < -90.0 || lat > 90.0 || lng < -180.0 || lng > 180.0) {
            throw new IllegalArgumentException("INVALID_COORDINATES");
        }

        User user = userRepository.findById(donorId)
                .orElseThrow(() -> new IllegalArgumentException("DONOR_NOT_FOUND"));

        if (!"donor".equalsIgnoreCase(user.getRole())) {
            throw new IllegalArgumentException("NOT_A_DONOR");
        }

        user.setLat(lat);
        user.setLng(lng);
        user.setLocationUpdatedAt(LocalDateTime.now());
        userRepository.save(user);

        Map<String, Object> response = new HashMap<>();
        response.put("id", donorId);
        response.put("lat", user.getLat());
        response.put("lng", user.getLng());
        response.put("locationUpdatedAt", user.getLocationUpdatedAt().toString());
        return response;
    }

    @Transactional(readOnly = true)
    public List<DonorAlertDTO> getDonorAlerts(String donorId) {
        if (donorId == null || donorId.trim().isEmpty()) {
            throw new IllegalArgumentException("DONOR_NOT_FOUND");
        }

        User user = userRepository.findById(donorId)
                .orElseThrow(() -> new IllegalArgumentException("DONOR_NOT_FOUND"));

        if (!"donor".equalsIgnoreCase(user.getRole())) {
            throw new IllegalArgumentException("NOT_A_DONOR");
        }

        List<RequestDonor> requestDonors = requestDonorRepository.findByDonorId(donorId);
        List<DonorAlertDTO> alertList = new ArrayList<>();

        for (RequestDonor rd : requestDonors) {
            if ("unalerted".equalsIgnoreCase(rd.getStatus()) || "matched".equalsIgnoreCase(rd.getAlertType())) {
                continue;
            }
            BloodRequest br = rd.getBloodRequest();
            if (br == null) continue;

            User hospital = br.getHospitalId() != null ? userRepository.findById(br.getHospitalId()).orElse(null) : null;

            DonorAlertDTO dto = new DonorAlertDTO();
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
            dto.setDType(rd.getAlertType());
            dto.setDStatus(rd.getStatus());
            dto.setRespondedAt(rd.getRespondedAt() != null ? rd.getRespondedAt().toString() : null);
            dto.setCreatedAt(br.getCreatedAt() != null ? br.getCreatedAt().toString() : null);

            alertList.add(dto);
        }

        alertList.sort((a, b) -> {
            if (a.getCreatedAt() == null || b.getCreatedAt() == null) return 0;
            return b.getCreatedAt().compareTo(a.getCreatedAt());
        });

        return alertList;
    }

    @Transactional
    public Map<String, Object> respondToAlert(String donorId, String requestId, String newStatus) {
        return respondToAlert(donorId, requestId, new AlertResponseRequest(newStatus));
    }

    @Transactional
    public Map<String, Object> respondToAlert(String donorId, String requestId, AlertResponseRequest request) {
        if (donorId == null || requestId == null || request == null || request.getStatus() == null) {
            throw new IllegalArgumentException("INVALID_STATUS");
        }

        String normalizedStatus = request.getStatus().trim().toLowerCase();
        if (normalizedStatus.equals("declined")) {
            normalizedStatus = "rejected";
        }

        if (!normalizedStatus.equals("accepted") && !normalizedStatus.equals("rejected") && !normalizedStatus.equals("pending")) {
            throw new IllegalArgumentException("INVALID_STATUS");
        }

        User user = userRepository.findById(donorId)
                .orElseThrow(() -> new IllegalArgumentException("DONOR_NOT_FOUND"));

        if (!"donor".equalsIgnoreCase(user.getRole())) {
            throw new IllegalArgumentException("NOT_A_DONOR");
        }

        RequestDonor rd = requestDonorRepository.findByDonorIdAndBloodRequestId(donorId, requestId)
                .orElseThrow(() -> new IllegalArgumentException("ALERT_NOT_FOUND"));

        BloodRequest br = rd.getBloodRequest();
        if (br == null) {
            br = bloodRequestRepository.findById(requestId).orElse(null);
        }

        if (br != null && "SCHEDULED".equalsIgnoreCase(br.getRequestType()) && br.getAccumulationDeadline() != null) {
            if (LocalDateTime.now().isAfter(br.getAccumulationDeadline())) {
                throw new IllegalArgumentException("ACCUMULATION_DEADLINE_PASSED");
            }
        }

        if ("accepted".equalsIgnoreCase(normalizedStatus)) {
            if ("closed".equalsIgnoreCase(rd.getStatus()) || (br != null && ("fulfilled".equalsIgnoreCase(br.getStatus()) || (br.getSecuredUnits() != null && br.getUnits() != null && br.getSecuredUnits() >= br.getUnits())))) {
                rd.setStatus("closed");
                requestDonorRepository.save(rd);
                throw new IllegalArgumentException("REQUEST_ALREADY_FULFILLED");
            }
        }

        // Handle fresh GPS coordinates on Accept
        Double lat = request.getLat();
        Double lng = request.getLng();
        boolean hasFreshGps = false;

        if ("accepted".equalsIgnoreCase(normalizedStatus) && (lat != null || lng != null)) {
            if (lat == null || lng == null || lat.isNaN() || lat.isInfinite() || lng.isNaN() || lng.isInfinite()
                    || lat < -90.0 || lat > 90.0 || lng < -180.0 || lng > 180.0) {
                throw new IllegalArgumentException("INVALID_COORDINATES");
            }
            // Update ONLY live location fields (lat, lng, locationUpdatedAt). Registered lat/lng strictly preserved.
            user.setLat(lat);
            user.setLng(lng);
            user.setLocationUpdatedAt(LocalDateTime.now());
            userRepository.save(user);
            hasFreshGps = true;
        }

        rd.setStatus(normalizedStatus);
        rd.setRespondedAt(LocalDateTime.now());
        requestDonorRepository.save(rd);

        // Update unified securedUnits and status on BloodRequest in MySQL
        if (br != null) {
            int bankUnits = requestBloodBankRepository.findByBloodRequestId(requestId).stream()
                    .filter(b -> "accepted".equalsIgnoreCase(b.getResponseStatus()))
                    .mapToInt(b -> b.getUnitsSecured() != null ? b.getUnitsSecured() : 0)
                    .sum();

            int donorUnits = (int) requestDonorRepository.findByBloodRequestId(requestId).stream()
                    .filter(d -> "confirmed".equalsIgnoreCase(d.getStatus()))
                    .count();

            int totalSecured = bankUnits + donorUnits;
            br.setSecuredUnits(totalSecured);

            if (totalSecured >= (br.getUnits() != null ? br.getUnits() : 1)) {
                br.setStatus("fulfilled");
                br.setFulfilledAt(LocalDateTime.now());
                // Close remaining pending donor alerts
                List<RequestDonor> allDonors = requestDonorRepository.findByBloodRequestId(requestId);
                for (RequestDonor d : allDonors) {
                    if ("pending".equalsIgnoreCase(d.getStatus()) && !d.getId().equals(rd.getId())) {
                        d.setStatus("closed");
                        requestDonorRepository.save(d);
                    }
                }
                // Close remaining waiting blood banks
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
            bloodRequestRepository.save(br);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("id", requestId);
        response.put("donorId", donorId);
        response.put("status", rd.getStatus());
        response.put("respondedAt", rd.getRespondedAt().toString());

        // Calculate actual distance and final ranking score for responding donor
        if ("accepted".equalsIgnoreCase(normalizedStatus)) {
            User hospital = br != null && br.getHospitalId() != null ? userRepository.findById(br.getHospitalId()).orElse(null) : null;
            if (hospital != null && hospital.getLat() != null && hospital.getLng() != null) {
                Double donorLat = user.getLat();
                Double donorLng = user.getLng();
                if (donorLat != null && donorLng != null) {
                    Double actualDistance = calculateDistance(hospital.getLat(), hospital.getLng(), donorLat, donorLng);
                    if (actualDistance != null) {
                        response.put("distance", Math.round(actualDistance * 10.0) / 10.0);
                        // FinalRankScore = ActualDistance * 1.5 with no penalties
                        response.put("rankScore", actualDistance * 1.5);
                        response.put("freshGps", true);
                    }
                }
            }
        }

        return response;
    }

    @Transactional(readOnly = true)
    public long getAvailableDonorCount() {
        return userRepository.countByRoleIgnoreCaseAndAvailableTrue("donor");
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

    @Transactional(readOnly = true)
    public List<DonorSearchItemDTO> searchDonors(String bloodGroup, Boolean available, String city, Double lat, Double lng, Integer radius) {
        List<User> donors = userRepository.findByRoleIgnoreCase("donor");
        List<DonorSearchItemDTO> results = new ArrayList<>();

        String normBg = (bloodGroup != null && !bloodGroup.trim().isEmpty()) ? bloodGroup.trim().toUpperCase() : null;
        String normCity = (city != null && !city.trim().isEmpty()) ? city.trim().toLowerCase() : null;

        for (User d : donors) {
            if (normBg != null && (d.getBloodGroup() == null || !normBg.equalsIgnoreCase(d.getBloodGroup().trim()))) {
                continue;
            }

            if (Boolean.TRUE.equals(available) && !Boolean.TRUE.equals(d.getAvailable())) {
                continue;
            }

            if (normCity != null && (d.getCity() == null || !d.getCity().toLowerCase().contains(normCity))) {
                continue;
            }

            Double distance = null;
            if (lat != null && lng != null && d.getLat() != null && d.getLng() != null) {
                distance = calculateDistance(lat, lng, d.getLat(), d.getLng());
                if (distance != null) {
                    distance = Math.round(distance * 10.0) / 10.0;
                }
            }

            if (lat != null && lng != null && radius != null && radius < 50) {
                if (distance != null && distance > radius) {
                    continue;
                }
            }

            DonorSearchItemDTO dto = new DonorSearchItemDTO();
            dto.setId(d.getId());
            dto.setName(d.getName());
            dto.setBloodGroup(d.getBloodGroup());
            dto.setPhone(d.getPhone());
            dto.setCity(d.getCity());
            dto.setState(d.getState());
            dto.setAge(d.getAge());
            dto.setAvailable(d.getAvailable());
            dto.setLastDonation(d.getLastDonation());
            dto.setNextEligibleDate(d.getNextEligibleDate());
            dto.setEligible(d.isEligibleToDonate());
            dto.setReportData(d.getReportData());
            dto.setReportName(d.getReportName());
            dto.setLat(d.getLat());
            dto.setLng(d.getLng());
            dto.setLocationUpdatedAt(d.getLocationUpdatedAt() != null ? d.getLocationUpdatedAt().toString() : null);
            dto.setDistance(distance);

            results.add(dto);
        }

        results.sort((a, b) -> {
            double distA = a.getDistance() != null ? a.getDistance() : 999.0;
            double distB = b.getDistance() != null ? b.getDistance() : 999.0;
            return Double.compare(distA, distB);
        });

        if (results.size() > 60) {
            results = new ArrayList<>(results.subList(0, 60));
        }

        return results;
    }
}
