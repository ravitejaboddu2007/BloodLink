package com.bloodlink.service;

import com.bloodlink.dto.UserProfileDTO;
import com.bloodlink.entity.User;
import com.bloodlink.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;

    @Autowired
    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public UserProfileDTO getUserProfile(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            throw new IllegalArgumentException("USER_NOT_FOUND");
        }

        User user = userRepository.findById(userId.trim())
                .orElseThrow(() -> new IllegalArgumentException("USER_NOT_FOUND"));

        UserProfileDTO dto = new UserProfileDTO();
        dto.setId(user.getId());
        dto.setRole(user.getRole());
        dto.setName(user.getName());
        dto.setEmail(user.getEmail());
        dto.setPhone(user.getPhone());
        dto.setAge(user.getAge());
        dto.setBloodGroup(user.getBloodGroup());
        dto.setCity(user.getCity());
        dto.setState(user.getState());
        dto.setAddress("bloodbank".equalsIgnoreCase(user.getRole()) ? null : user.getAddress());
        dto.setRegistrationNumber(user.getRegistrationNumber());
        dto.setOperatingHours(user.getOperatingHours());
        dto.setLat(user.getLat());
        dto.setLng(user.getLng());
        dto.setLocationUpdatedAt(user.getLocationUpdatedAt() != null ? user.getLocationUpdatedAt().toString() : null);
        dto.setAvailable(user.getAvailable());
        dto.setLastDonation(user.getLastDonation());
        dto.setNextEligibleDate(user.getNextEligibleDate());
        dto.setEligible(user.isEligibleToDonate());
        dto.setReportData(user.getReportData());
        dto.setReportName(user.getReportName());
        dto.setCreatedAt(user.getCreatedAt() != null ? user.getCreatedAt().toString() : null);

        return dto;
    }

    @Transactional(readOnly = true)
    public boolean existsUser(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            return false;
        }
        return userRepository.existsById(userId.trim());
    }

    @Transactional(readOnly = true)
    public java.util.Map<String, Long> getUserStats() {
        java.util.Map<String, Long> stats = new java.util.LinkedHashMap<>();
        stats.put("donors", userRepository.countByRoleIgnoreCase("donor"));
        stats.put("hospitals", userRepository.countByRoleIgnoreCase("hospital"));
        stats.put("bloodbanks", userRepository.countByRoleIgnoreCase("bloodbank"));
        stats.put("total", userRepository.count());
        return stats;
    }

    @Transactional
    public UserProfileDTO updateUserProfile(String userId, com.bloodlink.dto.UpdateProfileRequest req) {
        if (userId == null || userId.trim().isEmpty()) {
            throw new IllegalArgumentException("USER_NOT_FOUND");
        }

        User user = userRepository.findById(userId.trim())
                .orElseThrow(() -> new IllegalArgumentException("USER_NOT_FOUND"));

        if (req != null) {
            if (req.getName() != null && !req.getName().trim().isEmpty()) {
                user.setName(req.getName().trim());
            }
            if (req.getPhone() != null && !req.getPhone().trim().isEmpty()) {
                user.setPhone(req.getPhone().trim());
            }
            if (req.getAge() != null) {
                user.setAge(req.getAge());
            }
            if (req.getBloodGroup() != null && !req.getBloodGroup().trim().isEmpty()) {
                user.setBloodGroup(req.getBloodGroup().trim());
            }
            if (req.getCity() != null) {
                user.setCity(req.getCity().trim());
            }
            if (req.getState() != null) {
                user.setState(req.getState().trim());
            }
            if (!"bloodbank".equalsIgnoreCase(user.getRole())) {
                if (req.getAddress() != null) {
                    user.setAddress(req.getAddress().trim());
                }
            } else {
                user.setAddress(null);
            }
            if (req.getRegistrationNumber() != null) {
                user.setRegistrationNumber(req.getRegistrationNumber().trim());
            }
            if (req.getOperatingHours() != null) {
                user.setOperatingHours(req.getOperatingHours().trim());
            }
            if (req.getLat() != null) {
                user.setLat(req.getLat());
                user.setRegisteredLat(req.getLat());
            }
            if (req.getLng() != null) {
                user.setLng(req.getLng());
                user.setRegisteredLng(req.getLng());
            }
            if (req.getLocationUpdatedAt() != null) {
                try {
                    user.setLocationUpdatedAt(java.time.LocalDateTime.parse(req.getLocationUpdatedAt()));
                } catch (Exception e) {
                    user.setLocationUpdatedAt(java.time.LocalDateTime.now());
                }
            } else if (req.getLat() != null && req.getLng() != null) {
                user.setLocationUpdatedAt(java.time.LocalDateTime.now());
            }
            if (req.getAvailable() != null) {
                user.setAvailable(req.getAvailable());
            }
            if (req.getLastDonation() != null) {
                user.setLastDonation(req.getLastDonation());
                if (req.getLastDonation().trim().isEmpty()) {
                    user.setNextEligibleDate(null);
                } else {
                    try {
                        java.time.LocalDate dDate = java.time.LocalDate.parse(req.getLastDonation().trim());
                        user.setNextEligibleDate(dDate.plusDays(90).toString());
                    } catch (Exception ignored) {}
                }
            }
            if (req.getReportData() != null) {
                user.setReportData(req.getReportData());
            }
            if (req.getReportName() != null) {
                user.setReportName(req.getReportName());
            }
        }

        User saved = userRepository.save(user);
        return getUserProfile(saved.getId());
    }
}