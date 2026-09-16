package com.bloodlink;

import com.bloodlink.controller.UserController;
import com.bloodlink.dto.UserProfileDTO;
import com.bloodlink.entity.User;
import com.bloodlink.repository.UserRepository;
import com.bloodlink.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

public class UserServiceTest {

    private UserRepository userRepository;
    private UserService userService;
    private UserController userController;

    @BeforeEach
    public void setUp() {
        userRepository = Mockito.mock(UserRepository.class);
        userService = new UserService(userRepository);
        userController = new UserController(userService);
    }

    private User createSampleUser(String id, String role, String name, String email) {
        User u = new User();
        u.setId(id);
        u.setRole(role);
        u.setName(name);
        u.setEmail(email);
        u.setPasswordHash("$2a$10$hashedpasswordstring1234567890");
        u.setPhone("9876543210");
        u.setAge(28);
        u.setBloodGroup("O+");
        u.setCity("Mumbai");
        u.setState("Maharashtra");
        u.setAddress("123 Marine Drive");
        u.setRegistrationNumber("REG-999");
        u.setOperatingHours("24/7");
        u.setLat(18.9220);
        u.setLng(72.8347);
        u.setLocationUpdatedAt(LocalDateTime.now());
        u.setAvailable(true);
        u.setLastDonation("2026-01-15");
        u.setReportData("data:image/png;base64,sample");
        u.setReportName("blood_test.pdf");
        u.setCreatedAt(LocalDateTime.now().minusMonths(2));
        return u;
    }

    @Test
    public void testExistingUserIdReturnsCorrectProfile() {
        User sample = createSampleUser("d_1001", "donor", "John Doe", "john@example.com");
        when(userRepository.findById("d_1001")).thenReturn(Optional.of(sample));

        UserProfileDTO profile = userService.getUserProfile("d_1001");

        assertNotNull(profile);
        assertEquals("d_1001", profile.getId());
        assertEquals("donor", profile.getRole());
        assertEquals("John Doe", profile.getName());
        assertEquals("john@example.com", profile.getEmail());
        assertEquals("9876543210", profile.getPhone());
        assertEquals(28, profile.getAge());
        assertEquals("O+", profile.getBloodGroup());
        assertEquals("Mumbai", profile.getCity());
        assertEquals("Maharashtra", profile.getState());
        assertEquals("123 Marine Drive", profile.getAddress());
        assertEquals("REG-999", profile.getRegistrationNumber());
        assertEquals("24/7", profile.getOperatingHours());
        assertEquals(18.9220, profile.getLat());
        assertEquals(72.8347, profile.getLng());
        assertTrue(profile.getAvailable());
        assertEquals("2026-01-15", profile.getLastDonation());
        assertEquals("data:image/png;base64,sample", profile.getReportData());
        assertEquals("blood_test.pdf", profile.getReportName());
        assertNotNull(profile.getLocationUpdatedAt());
        assertNotNull(profile.getCreatedAt());
    }

    @Test
    public void testUnknownUserIdThrowsNotFoundException() {
        when(userRepository.findById("unknown_user")).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            userService.getUserProfile("unknown_user");
        });
        assertEquals("USER_NOT_FOUND", ex.getMessage());
    }

    @Test
    public void testNullOrEmptyUserIdThrowsNotFoundException() {
        assertThrows(IllegalArgumentException.class, () -> {
            userService.getUserProfile(null);
        });
        assertThrows(IllegalArgumentException.class, () -> {
            userService.getUserProfile("   ");
        });
    }

    @Test
    public void testPasswordAndHashNeverExposedInProfileDTO() {
        for (Field f : UserProfileDTO.class.getDeclaredFields()) {
            String name = f.getName().toLowerCase();
            assertFalse(name.contains("password"), "UserProfileDTO must never contain password fields: " + f.getName());
            assertFalse(name.contains("passwordhash"), "UserProfileDTO must never contain passwordHash fields: " + f.getName());
        }
        for (Method m : UserProfileDTO.class.getDeclaredMethods()) {
            String name = m.getName().toLowerCase();
            assertFalse(name.contains("password"), "UserProfileDTO must never contain password methods: " + m.getName());
            assertFalse(name.contains("passwordhash"), "UserProfileDTO must never contain passwordHash methods: " + m.getName());
        }
    }

    @Test
    public void testUserControllerGetProfileSuccess() {
        User sample = createSampleUser("h_2002", "hospital", "City Hospital", "hospital@example.com");
        when(userRepository.findById("h_2002")).thenReturn(Optional.of(sample));

        ResponseEntity<?> response = userController.getUserProfile("h_2002");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody() instanceof UserProfileDTO);
        UserProfileDTO body = (UserProfileDTO) response.getBody();
        assertEquals("h_2002", body.getId());
        assertEquals("City Hospital", body.getName());
    }

    @Test
    public void testUserControllerGetProfileNotFound() {
        when(userRepository.findById("invalid_id")).thenReturn(Optional.empty());

        ResponseEntity<?> response = userController.getUserProfile("invalid_id");

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    public void testBloodBankProfileAddressIsAlwaysNull() {
        User bank = createSampleUser("b_3003", "bloodbank", "Red Cross Bank", "redcross@example.com");
        bank.setAddress("Legacy Stored Address");
        when(userRepository.findById("b_3003")).thenReturn(Optional.of(bank));

        UserProfileDTO profile = userService.getUserProfile("b_3003");

        assertNotNull(profile);
        assertEquals("b_3003", profile.getId());
        assertEquals("bloodbank", profile.getRole());
        assertNull(profile.getAddress(), "Blood bank profile address must always be null");
        assertEquals("Mumbai", profile.getCity());
        assertEquals("Maharashtra", profile.getState());
        assertEquals(18.9220, profile.getLat());
        assertEquals(72.8347, profile.getLng());
    }

    @Test
    public void testBloodBankProfileUpdateIgnoresAddress() {
        User bank = createSampleUser("b_3003", "bloodbank", "Red Cross Bank", "redcross@example.com");
        when(userRepository.findById("b_3003")).thenReturn(Optional.of(bank));
        when(userRepository.save(Mockito.any(User.class))).thenAnswer(i -> i.getArgument(0));

        com.bloodlink.dto.UpdateProfileRequest req = new com.bloodlink.dto.UpdateProfileRequest();
        req.setName("Red Cross Updated");
        req.setAddress("Attempted Address Injection");
        req.setCity("Pune");
        req.setState("Maharashtra");

        UserProfileDTO updated = userService.updateUserProfile("b_3003", req);

        assertNotNull(updated);
        assertEquals("Red Cross Updated", updated.getName());
        assertEquals("Pune", updated.getCity());
        assertNull(updated.getAddress(), "Blood bank address must not be saved or returned");
        assertNull(bank.getAddress(), "User entity address for bloodbank must be null");
    }

    @Test
    public void testHospitalProfileRetainsAddress() {
        User hospital = createSampleUser("h_2002", "hospital", "City Hospital", "hospital@example.com");
        hospital.setAddress("123 Hospital Way");
        when(userRepository.findById("h_2002")).thenReturn(Optional.of(hospital));
        when(userRepository.save(Mockito.any(User.class))).thenAnswer(i -> i.getArgument(0));

        UserProfileDTO profile = userService.getUserProfile("h_2002");
        assertEquals("123 Hospital Way", profile.getAddress(), "Hospital address must be preserved");

        com.bloodlink.dto.UpdateProfileRequest req = new com.bloodlink.dto.UpdateProfileRequest();
        req.setAddress("456 New Hospital Rd");
        UserProfileDTO updated = userService.updateUserProfile("h_2002", req);
        assertEquals("456 New Hospital Rd", updated.getAddress(), "Hospital address update must succeed");
    }
}