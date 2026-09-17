package com.bloodlink.service;

import com.bloodlink.dto.CreateDonationRequest;
import com.bloodlink.dto.DonationHistoryDTO;
import com.bloodlink.entity.DonationHistory;
import com.bloodlink.entity.User;
import com.bloodlink.repository.DonationHistoryRepository;
import com.bloodlink.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class DonationHistoryService {

    private final DonationHistoryRepository donationHistoryRepository;
    private final UserRepository userRepository;

    @Autowired
    public DonationHistoryService(DonationHistoryRepository donationHistoryRepository,
                                  UserRepository userRepository) {
        this.donationHistoryRepository = donationHistoryRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<DonationHistoryDTO> getDonationHistory(String donorId) {
        if (donorId == null || donorId.trim().isEmpty()) {
            throw new IllegalArgumentException("DONOR_NOT_FOUND");
        }

        User user = userRepository.findById(donorId.trim())
                .orElseThrow(() -> new IllegalArgumentException("DONOR_NOT_FOUND"));

        if (!"donor".equalsIgnoreCase(user.getRole())) {
            throw new IllegalArgumentException("NOT_A_DONOR");
        }

        List<DonationHistory> list = donationHistoryRepository.findByDonorIdOrderByDateDesc(donorId.trim());
        List<DonationHistoryDTO> dtos = new ArrayList<>();

        for (DonationHistory h : list) {
            DonationHistoryDTO dto = new DonationHistoryDTO(
                    h.getId(),
                    h.getDonorId(),
                    h.getDate(),
                    h.getLocation(),
                    h.getUnits(),
                    h.getNotes(),
                    h.getCreatedAt() != null ? h.getCreatedAt().toString() : null
            );
            dtos.add(dto);
        }

        dtos.sort((a, b) -> {
            if (a.getDate() == null && b.getDate() == null) return 0;
            if (a.getDate() == null) return 1;
            if (b.getDate() == null) return -1;
            int cmp = b.getDate().compareTo(a.getDate());
            if (cmp != 0) return cmp;
            if (a.getCreatedAt() == null || b.getCreatedAt() == null) return 0;
            return b.getCreatedAt().compareTo(a.getCreatedAt());
        });

        return dtos;
    }

    @Transactional
    public DonationHistoryDTO createDonation(String donorId, CreateDonationRequest req) {
        if (donorId == null || donorId.trim().isEmpty()) {
            throw new IllegalArgumentException("DONOR_NOT_FOUND");
        }
        if (req == null) {
            throw new IllegalArgumentException("MISSING_DATA");
        }
        if (req.getDate() == null || req.getDate().trim().isEmpty()) {
            throw new IllegalArgumentException("MISSING_DATE");
        }
        if (req.getLocation() == null || req.getLocation().trim().isEmpty()) {
            throw new IllegalArgumentException("MISSING_LOCATION");
        }

        User user = userRepository.findById(donorId.trim())
                .orElseThrow(() -> new IllegalArgumentException("DONOR_NOT_FOUND"));

        if (!"donor".equalsIgnoreCase(user.getRole())) {
            throw new IllegalArgumentException("NOT_A_DONOR");
        }

        String date = req.getDate().trim();
        String location = req.getLocation().trim();
        int units = (req.getUnits() != null && req.getUnits() > 0) ? req.getUnits() : 1;
        String notes = req.getNotes() != null ? req.getNotes().trim() : null;

        String id = "dh_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        LocalDateTime now = LocalDateTime.now();

        DonationHistory history = new DonationHistory();
        history.setId(id);
        history.setDonorId(user.getId());
        history.setDate(date);
        history.setLocation(location);
        history.setUnits(units);
        history.setNotes(notes);
        history.setCreatedAt(now);

        donationHistoryRepository.save(history);

        // Update donor's lastDonation and nextEligibleDate in MySQL users table if this donation is newer or unset
        if (user.getLastDonation() == null || user.getLastDonation().trim().isEmpty() || date.compareTo(user.getLastDonation()) > 0) {
            user.setLastDonation(date);
            try {
                java.time.LocalDate dDate = java.time.LocalDate.parse(date);
                int restingDays = "FEMALE".equalsIgnoreCase(user.getGender()) ? 112 : 84;
                user.setNextEligibleDate(dDate.plusDays(restingDays).toString());
            } catch (Exception ignored) {}
            userRepository.save(user);
        }

        return new DonationHistoryDTO(
                history.getId(),
                history.getDonorId(),
                history.getDate(),
                history.getLocation(),
                history.getUnits(),
                history.getNotes(),
                history.getCreatedAt().toString()
        );
    }
}