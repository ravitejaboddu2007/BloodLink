package com.bloodlink.service;

import com.bloodlink.dto.LoginRequest;
import com.bloodlink.dto.LoginResponse;
import com.bloodlink.dto.SignupRequest;
import com.bloodlink.dto.SignupResponse;
import com.bloodlink.entity.Inventory;
import com.bloodlink.entity.User;
import com.bloodlink.repository.InventoryRepository;
import com.bloodlink.repository.UserRepository;
import com.bloodlink.security.JwtService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Service
public class AuthService {

    private static final List<String> VALID_BLOOD_GROUPS = Arrays.asList(
            "A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-"
    );

    private final UserRepository userRepository;
    private final InventoryRepository inventoryRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository, InventoryRepository inventoryRepository) {
        this(userRepository, inventoryRepository,
                new JwtService("404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970", 604800000L),
                new BCryptPasswordEncoder());
    }

    @Autowired
    public AuthService(UserRepository userRepository, InventoryRepository inventoryRepository,
                       JwtService jwtService, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.inventoryRepository = inventoryRepository;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder != null ? passwordEncoder : new BCryptPasswordEncoder();
    }

    private void validateSignupRequest(SignupRequest req) {
        if (req == null) {
            throw new IllegalArgumentException("Required registration fields missing");
        }

        if (req.getRole() == null || req.getRole().trim().isEmpty()) {
            throw new IllegalArgumentException("Role is required");
        }

        String role = req.getRole().trim().toLowerCase();
        if (!role.equals("donor") && !role.equals("hospital") && !role.equals("bloodbank")) {
            throw new IllegalArgumentException("Invalid role specified");
        }

        // Name validation
        if (req.getName() == null || req.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Name is required");
        }
        String name = req.getName().trim();
        if (name.matches("^\\d+$")) {
            throw new IllegalArgumentException("Name must contain valid text, not only numbers");
        }

        // Email validation
        if (req.getEmail() == null || req.getEmail().trim().isEmpty()) {
            throw new IllegalArgumentException("Email address is required");
        }
        String email = req.getEmail().trim().toLowerCase();
        if (!email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new IllegalArgumentException("Valid email address required");
        }

        // Phone validation (Indian 10-digit mobile)
        if (req.getPhone() == null || req.getPhone().trim().isEmpty()) {
            throw new IllegalArgumentException("Phone number is required");
        }
        String phone = req.getPhone().trim();
        if (!phone.matches("^[6-9]\\d{9}$")) {
            throw new IllegalArgumentException("Phone number must be a valid 10-digit Indian mobile number starting with 6-9");
        }

        // Password validation (min 8 chars, at least 1 letter, at least 1 number)
        if (req.getPassword() == null || req.getPassword().isEmpty()) {
            throw new IllegalArgumentException("Password is required");
        }
        if (!req.getPassword().matches("^(?=.*[A-Za-z])(?=.*\\d).{8,}$")) {
            throw new IllegalArgumentException("Password must be at least 8 characters long and contain both letters and numbers");
        }

        // Location coordinates (if supplied)
        if (req.getLat() != null) {
            if (req.getLat() < -90.0 || req.getLat() > 90.0) {
                throw new IllegalArgumentException("Latitude must be between -90 and 90");
            }
        }
        if (req.getLng() != null) {
            if (req.getLng() < -180.0 || req.getLng() > 180.0) {
                throw new IllegalArgumentException("Longitude must be between -180 and 180");
            }
        }

        // Role-specific validations
        if (role.equals("donor")) {
            if (req.getAge() == null) {
                throw new IllegalArgumentException("Age is required");
            }
            if (req.getAge() < 18 || req.getAge() > 65) {
                throw new IllegalArgumentException("Donor age must be between 18 and 65");
            }

            if (req.getGender() == null || req.getGender().trim().isEmpty()) {
                throw new IllegalArgumentException("Gender is required");
            }
            String gender = req.getGender().trim().toUpperCase();
            if (!gender.equals("MALE") && !gender.equals("FEMALE")) {
                throw new IllegalArgumentException("Gender must be MALE or FEMALE");
            }

            if (req.getBloodGroup() == null || req.getBloodGroup().trim().isEmpty()) {
                throw new IllegalArgumentException("Blood group is required");
            }
            String bg = req.getBloodGroup().trim();
            if (!VALID_BLOOD_GROUPS.contains(bg)) {
                throw new IllegalArgumentException("Blood group must be one of A+, A-, B+, B-, AB+, AB-, O+, O-");
            }

            if (req.getCity() == null || req.getCity().trim().isEmpty()) {
                throw new IllegalArgumentException("City is required");
            }
        } else if (role.equals("hospital")) {
            if (req.getRegistrationNumber() == null || req.getRegistrationNumber().trim().isEmpty()) {
                throw new IllegalArgumentException("Hospital registration / license number is required");
            }
            if (req.getCity() == null || req.getCity().trim().isEmpty()) {
                throw new IllegalArgumentException("City is required");
            }
        } else if (role.equals("bloodbank")) {
            if (req.getRegistrationNumber() == null || req.getRegistrationNumber().trim().isEmpty()) {
                throw new IllegalArgumentException("Blood bank registration / license ID is required");
            }
            if (req.getOperatingHours() == null || req.getOperatingHours().trim().isEmpty()) {
                throw new IllegalArgumentException("Operating hours are required");
            }
            if (req.getCity() == null || req.getCity().trim().isEmpty()) {
                throw new IllegalArgumentException("City is required");
            }
        }
    }

    @Transactional
    public SignupResponse registerUser(SignupRequest req) {
        validateSignupRequest(req);

        String role = req.getRole().toLowerCase().trim();
        String email = req.getEmail().trim().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("EMAIL_EXISTS");
        }

        if ((role.equals("bloodbank") || role.equals("hospital")) && req.getRegistrationNumber() != null) {
            String regNum = req.getRegistrationNumber().trim();
            if (!regNum.isEmpty() && userRepository.existsByRoleAndRegistrationNumberIgnoreCase(role, regNum)) {
                throw new IllegalArgumentException("LICENSE_EXISTS");
            }
        }

        String prefix = role.equals("hospital") ? "h_" : role.equals("bloodbank") ? "b_" : "d_";
        String userId = prefix + System.currentTimeMillis();

        User user = new User();
        user.setId(userId);
        user.setRole(role);
        user.setName(req.getName().trim());
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        user.setPhone(req.getPhone().trim());
        user.setAge(req.getAge());
        user.setGender(role.equals("donor") && req.getGender() != null && !req.getGender().trim().isEmpty() ? req.getGender().trim().toUpperCase() : null);
        user.setBloodGroup(req.getBloodGroup() != null ? req.getBloodGroup().trim() : null);
        user.setCity(req.getCity() != null ? req.getCity().trim() : null);
        user.setState(req.getState() != null ? req.getState().trim() : null);
        user.setAddress(role.equals("bloodbank") ? null : (req.getAddress() != null ? req.getAddress().trim() : null));
        user.setRegistrationNumber(req.getRegistrationNumber() != null ? req.getRegistrationNumber().trim() : null);
        user.setOperatingHours(req.getOperatingHours() != null ? req.getOperatingHours().trim() : null);
        user.setLat(req.getLat());
        user.setLng(req.getLng());
        user.setRegisteredLat(req.getLat());
        user.setRegisteredLng(req.getLng());
        if (req.getLat() != null && req.getLng() != null) {
            user.setLocationUpdatedAt(LocalDateTime.now());
        }
        user.setAvailable(role.equals("donor") ? true : null);
        user.setLastDonation(null);
        user.setReportData(req.getReportData());
        user.setReportName(req.getReportName());
        user.setCreatedAt(LocalDateTime.now());

        userRepository.save(user);

        // Transactional Blood Bank zero-stock inventory initialization
        if (role.equals("bloodbank")) {
            Inventory inv = new Inventory();
            inv.setBloodBankId(userId);
            inv.setStockAPos(0);
            inv.setStockANeg(0);
            inv.setStockBPos(0);
            inv.setStockBNeg(0);
            inv.setStockOPos(0);
            inv.setStockONeg(0);
            inv.setStockAbPos(0);
            inv.setStockAbNeg(0);
            inventoryRepository.save(inv);
        }

        SignupResponse res = new SignupResponse();
        res.setId(user.getId());
        res.setRole(user.getRole());
        res.setName(user.getName());
        res.setEmail(user.getEmail());
        res.setPhone(user.getPhone());
        res.setAge(user.getAge());
        res.setGender(user.getGender());
        res.setCity(user.getCity());
        res.setState(user.getState());
        res.setBloodGroup(user.getBloodGroup());
        res.setRegistrationNumber(user.getRegistrationNumber());
        res.setOperatingHours(user.getOperatingHours());
        res.setAddress(role.equals("bloodbank") ? null : user.getAddress());
        res.setLat(user.getLat());
        res.setLng(user.getLng());
        res.setAvailable(user.getAvailable());
        res.setCreatedAt(user.getCreatedAt().toString());
        res.setToken(jwtService.generateToken(user.getId(), user.getRole(), user.getEmail()));

        return res;
    }

    @Transactional(readOnly = true)
    public LoginResponse loginUser(LoginRequest req) {
        if (req == null || req.getEmail() == null || req.getEmail().trim().isEmpty() ||
            req.getPassword() == null || req.getPassword().isEmpty()) {
            throw new IllegalArgumentException("MISSING_CREDENTIALS");
        }

        String email = req.getEmail().trim().toLowerCase();
        if (!email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new IllegalArgumentException("INVALID_CREDENTIALS");
        }

        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new IllegalArgumentException("INVALID_CREDENTIALS"));

        if (!passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("INVALID_CREDENTIALS");
        }

        LoginResponse res = new LoginResponse();
        res.setId(user.getId());
        res.setRole(user.getRole());
        res.setName(user.getName());
        res.setEmail(user.getEmail());
        res.setPhone(user.getPhone());
        res.setAge(user.getAge());
        res.setGender(user.getGender());
        res.setCity(user.getCity());
        res.setState(user.getState());
        res.setBloodGroup(user.getBloodGroup());
        res.setRegistrationNumber(user.getRegistrationNumber());
        res.setOperatingHours(user.getOperatingHours());
        res.setAddress("bloodbank".equalsIgnoreCase(user.getRole()) ? null : user.getAddress());
        res.setLat(user.getLat());
        res.setLng(user.getLng());
        res.setAvailable(user.getAvailable());
        res.setLastDonation(user.getLastDonation());
        res.setNextEligibleDate(user.getNextEligibleDate());
        res.setEligible(user.isEligibleToDonate());
        res.setCreatedAt(user.getCreatedAt().toString());
        res.setToken(jwtService.generateToken(user.getId(), user.getRole(), user.getEmail()));

        return res;
    }
}
