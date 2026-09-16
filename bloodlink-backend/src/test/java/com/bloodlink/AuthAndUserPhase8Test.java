package com.bloodlink;

import com.bloodlink.controller.AuthController;
import com.bloodlink.controller.UserController;
import com.bloodlink.dto.*;
import com.bloodlink.entity.Inventory;
import com.bloodlink.entity.User;
import com.bloodlink.repository.InventoryRepository;
import com.bloodlink.repository.UserRepository;
import com.bloodlink.service.AuthService;
import com.bloodlink.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class AuthAndUserPhase8Test {

    private UserRepository userRepository;
    private InventoryRepository inventoryRepository;
    private AuthService authService;
    private UserService userService;
    private AuthController authController;
    private UserController userController;

    @BeforeEach
    public void setUp() {
        userRepository = Mockito.mock(UserRepository.class);
        inventoryRepository = Mockito.mock(InventoryRepository.class);

        authService = new AuthService(userRepository, inventoryRepository);
        userService = new UserService(userRepository);

        authController = new AuthController(authService);
        userController = new UserController(userService);
    }

    private User createSampleUser(String id, String role, String name, String email) {
        User u = new User();
        u.setId(id);
        u.setRole(role);
        u.setName(name);
        u.setEmail(email);
        u.setPasswordHash(new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode("password123"));
        u.setPhone("9876543210");
        u.setAge(28);
        u.setCity("Bengaluru");
        u.setCreatedAt(LocalDateTime.now());
        return u;
    }

    @Test
    public void testRegisterDonorSuccess() {
        when(userRepository.existsByEmail("donor@test.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        SignupRequest req = new SignupRequest();
        req.setRole("donor");
        req.setName("Rahul Sharma");
        req.setEmail("donor@test.com");
        req.setPhone("9876543210");
        req.setPassword("SecurePass123");
        req.setAge(25);
        req.setBloodGroup("O+");
        req.setCity("Bengaluru");

        SignupResponse res = authService.registerUser(req);

        assertNotNull(res);
        assertNotNull(res.getId());
        assertEquals("donor", res.getRole());
        assertEquals("Rahul Sharma", res.getName());
        assertEquals("donor@test.com", res.getEmail());
        assertEquals(25, res.getAge());
        assertTrue(res.getAvailable());
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    public void testRegisterBloodBankInitializesInventoryInMySQL() {
        when(userRepository.existsByEmail("bank@test.com")).thenReturn(false);
        when(userRepository.existsByRoleAndRegistrationNumberIgnoreCase("bloodbank", "LIC-12345")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        when(inventoryRepository.save(any(Inventory.class))).thenAnswer(i -> i.getArgument(0));

        SignupRequest req = new SignupRequest();
        req.setRole("bloodbank");
        req.setName("Rotary Blood Bank");
        req.setEmail("bank@test.com");
        req.setPhone("9876543210");
        req.setPassword("SecurePass123");
        req.setRegistrationNumber("LIC-12345");
        req.setAddress("MG Road");
        req.setOperatingHours("24 Hours");
        req.setCity("Bengaluru");

        SignupResponse res = authService.registerUser(req);

        assertNotNull(res);
        assertEquals("bloodbank", res.getRole());
        assertNull(res.getAddress(), "Blood bank registration must not return address");
        verify(inventoryRepository, times(1)).save(any(Inventory.class));
    }

    @Test
    public void testRegisterBloodBankWithoutAddressSuccess() {
        when(userRepository.existsByEmail("noaddress@test.com")).thenReturn(false);
        when(userRepository.existsByRoleAndRegistrationNumberIgnoreCase("bloodbank", "LIC-99999")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        when(inventoryRepository.save(any(Inventory.class))).thenAnswer(i -> i.getArgument(0));

        SignupRequest req = new SignupRequest();
        req.setRole("bloodbank");
        req.setName("Metro Blood Bank");
        req.setEmail("noaddress@test.com");
        req.setPhone("9876543210");
        req.setPassword("SecurePass123");
        req.setRegistrationNumber("LIC-99999");
        req.setAddress(null); // Address omitted / null
        req.setOperatingHours("24 Hours");
        req.setCity("Hyderabad");

        SignupResponse res = authService.registerUser(req);

        assertNotNull(res);
        assertEquals("bloodbank", res.getRole());
        assertEquals("Metro Blood Bank", res.getName());
        assertNull(res.getAddress(), "Blood bank registration must not return address");
        verify(inventoryRepository, times(1)).save(any(Inventory.class));
    }

    @Test
    public void testRegisterRejectsDuplicateEmail() {
        when(userRepository.existsByEmail("dup@test.com")).thenReturn(true);

        SignupRequest req = new SignupRequest();
        req.setRole("donor");
        req.setName("John");
        req.setEmail("dup@test.com");
        req.setPhone("9876543210");
        req.setPassword("Pass12345");
        req.setAge(22);
        req.setBloodGroup("A+");
        req.setCity("Pune");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            authService.registerUser(req);
        });
        assertEquals("EMAIL_EXISTS", ex.getMessage());
    }

    @Test
    public void testLoginSuccess() {
        User user = createSampleUser("d_1", "donor", "Rahul", "rahul@test.com");
        when(userRepository.findByEmailIgnoreCase("rahul@test.com")).thenReturn(Optional.of(user));

        LoginRequest req = new LoginRequest();
        req.setEmail("rahul@test.com");
        req.setPassword("password123");

        LoginResponse res = authService.loginUser(req);

        assertNotNull(res);
        assertEquals("d_1", res.getId());
        assertEquals("Rahul", res.getName());
        assertEquals(28, res.getAge());
    }

    @Test
    public void testLoginWrongPasswordThrows() {
        User user = createSampleUser("d_1", "donor", "Rahul", "rahul@test.com");
        when(userRepository.findByEmailIgnoreCase("rahul@test.com")).thenReturn(Optional.of(user));

        LoginRequest req = new LoginRequest();
        req.setEmail("rahul@test.com");
        req.setPassword("wrongpassword");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            authService.loginUser(req);
        });
        assertEquals("INVALID_CREDENTIALS", ex.getMessage());
    }

    @Test
    public void testUpdateUserProfileSuccess() {
        User user = createSampleUser("d_1", "donor", "Rahul", "rahul@test.com");
        when(userRepository.findById("d_1")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        UpdateProfileRequest req = new UpdateProfileRequest();
        req.setName("Rahul S. Kumar");
        req.setPhone("9988776655");
        req.setCity("Mysuru");
        req.setAvailable(false);

        UserProfileDTO updated = userService.updateUserProfile("d_1", req);

        assertNotNull(updated);
        assertEquals("Rahul S. Kumar", updated.getName());
        assertEquals("9988776655", updated.getPhone());
        assertEquals("Mysuru", updated.getCity());
        assertFalse(updated.getAvailable());
        verify(userRepository, times(1)).save(user);
    }

    @Test
    public void testExistsUser() {
        when(userRepository.existsById("u_100")).thenReturn(true);
        when(userRepository.existsById("unknown")).thenReturn(false);

        assertTrue(userService.existsUser("u_100"));
        assertFalse(userService.existsUser("unknown"));
        assertFalse(userService.existsUser(null));
    }

    @Test
    public void testGetUserStats() {
        when(userRepository.countByRoleIgnoreCase("donor")).thenReturn(50L);
        when(userRepository.countByRoleIgnoreCase("hospital")).thenReturn(10L);
        when(userRepository.countByRoleIgnoreCase("bloodbank")).thenReturn(5L);
        when(userRepository.count()).thenReturn(65L);

        Map<String, Long> stats = userService.getUserStats();

        assertNotNull(stats);
        assertEquals(50L, stats.get("donors"));
        assertEquals(10L, stats.get("hospitals"));
        assertEquals(5L, stats.get("bloodbanks"));
        assertEquals(65L, stats.get("total"));
    }

    @Test
    public void testControllerGetUserStatsEndpoint() {
        when(userRepository.countByRoleIgnoreCase("donor")).thenReturn(20L);
        when(userRepository.countByRoleIgnoreCase("hospital")).thenReturn(5L);
        when(userRepository.countByRoleIgnoreCase("bloodbank")).thenReturn(2L);
        when(userRepository.count()).thenReturn(27L);

        ResponseEntity<?> res = userController.getUserStats();

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertTrue(res.getBody() instanceof Map);
        Map<?, ?> map = (Map<?, ?>) res.getBody();
        assertEquals(20L, map.get("donors"));
    }

    @Test
    public void testControllerCheckUserExistsEndpoint() {
        when(userRepository.existsById("h_1")).thenReturn(true);

        ResponseEntity<?> res = userController.checkUserExists("h_1");

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertTrue(res.getBody() instanceof Map);
        Map<?, ?> map = (Map<?, ?>) res.getBody();
        assertEquals(true, map.get("exists"));
    }

    @Test
    public void testControllerUpdateUserProfileEndpoint() {
        User user = createSampleUser("d_1", "donor", "Rahul", "rahul@test.com");
        when(userRepository.findById("d_1")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        UpdateProfileRequest req = new UpdateProfileRequest();
        req.setName("Rahul Updated");

        ResponseEntity<?> res = userController.updateUserProfile("d_1", req);

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertTrue(res.getBody() instanceof UserProfileDTO);
        UserProfileDTO dto = (UserProfileDTO) res.getBody();
        assertEquals("Rahul Updated", dto.getName());
    }

    @Test
    public void testAuthControllerSignupAndLoginEndpoints() {
        when(userRepository.existsByEmail("new@test.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        SignupRequest sReq = new SignupRequest();
        sReq.setRole("donor");
        sReq.setName("New Donor");
        sReq.setEmail("new@test.com");
        sReq.setPhone("9876543210");
        sReq.setPassword("Password123");
        sReq.setAge(30);
        sReq.setBloodGroup("B+");
        sReq.setCity("Chennai");

        ResponseEntity<?> signupRes = authController.signup(sReq);
        assertEquals(HttpStatus.CREATED, signupRes.getStatusCode());

        User created = createSampleUser("d_new", "donor", "New Donor", "new@test.com");
        when(userRepository.findByEmailIgnoreCase("new@test.com")).thenReturn(Optional.of(created));

        LoginRequest lReq = new LoginRequest();
        lReq.setEmail("new@test.com");
        lReq.setPassword("password123");

        ResponseEntity<?> loginRes = authController.login(lReq);
        assertEquals(HttpStatus.OK, loginRes.getStatusCode());
    }
}