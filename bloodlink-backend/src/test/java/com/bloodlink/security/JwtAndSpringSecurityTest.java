package com.bloodlink.security;

import com.bloodlink.dto.LoginRequest;
import com.bloodlink.dto.LoginResponse;
import com.bloodlink.dto.SignupRequest;
import com.bloodlink.dto.SignupResponse;
import com.bloodlink.entity.User;
import com.bloodlink.repository.InventoryRepository;
import com.bloodlink.repository.UserRepository;
import com.bloodlink.service.AuthService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class JwtAndSpringSecurityTest {

    private static final String TEST_SECRET = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";
    private static final long TEST_EXPIRATION_MS = 604800000L; // 7 days

    private JwtService jwtService;
    private UserRepository userRepository;
    private InventoryRepository inventoryRepository;
    private AuthService authService;
    private BCryptPasswordEncoder passwordEncoder;

    @BeforeEach
    public void setUp() {
        SecurityContextHolder.clearContext();
        jwtService = new JwtService(TEST_SECRET, TEST_EXPIRATION_MS);
        userRepository = Mockito.mock(UserRepository.class);
        inventoryRepository = Mockito.mock(InventoryRepository.class);
        passwordEncoder = new BCryptPasswordEncoder();
        authService = new AuthService(userRepository, inventoryRepository, jwtService, passwordEncoder);
    }

    @AfterEach
    public void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private User createSampleUser(String id, String role, String email, String plainPassword) {
        User u = new User();
        u.setId(id);
        u.setRole(role);
        u.setName("Test User");
        u.setEmail(email);
        u.setPasswordHash(passwordEncoder.encode(plainPassword));
        u.setPhone("9876543210");
        u.setAge(25);
        u.setBloodGroup("O+");
        u.setCity("Bengaluru");
        u.setCreatedAt(LocalDateTime.now());
        return u;
    }

    // TEST A: Valid email + valid password -> login succeeds -> JWT is returned.
    @Test
    public void testA_ValidEmailAndPasswordLoginReturnsJwtToken() {
        User user = createSampleUser("d_123", "donor", "donor@test.com", "Secret123");
        when(userRepository.findByEmailIgnoreCase("donor@test.com")).thenReturn(Optional.of(user));

        LoginRequest req = new LoginRequest();
        req.setEmail("donor@test.com");
        req.setPassword("Secret123");

        LoginResponse res = authService.loginUser(req);

        assertNotNull(res, "LoginResponse should not be null");
        assertEquals("d_123", res.getId());
        assertEquals("donor", res.getRole());
        assertEquals("donor@test.com", res.getEmail());
        assertNotNull(res.getToken(), "JWT token must be populated on successful login");
        assertTrue(jwtService.validateToken(res.getToken()), "Generated token must be valid");
        assertEquals("d_123", jwtService.extractUserId(res.getToken()));
        assertEquals("donor", jwtService.extractRole(res.getToken()));
    }

    // TEST B: Invalid password -> login fails.
    @Test
    public void testB_InvalidPasswordLoginFails() {
        User user = createSampleUser("d_123", "donor", "donor@test.com", "Secret123");
        when(userRepository.findByEmailIgnoreCase("donor@test.com")).thenReturn(Optional.of(user));

        LoginRequest req = new LoginRequest();
        req.setEmail("donor@test.com");
        req.setPassword("WrongPassword123");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            authService.loginUser(req);
        });

        assertEquals("INVALID_CREDENTIALS", ex.getMessage());
    }

    // TEST C: Valid JWT -> protected endpoint / filter is recognized as authenticated.
    @Test
    public void testC_ValidJwtAuthenticatesAndExtractsClaims() {
        String token = jwtService.generateToken("h_999", "hospital", "hospital@test.com");

        assertTrue(jwtService.validateToken(token));
        assertEquals("h_999", jwtService.extractUserId(token));
        assertEquals("hospital", jwtService.extractRole(token));
        assertEquals("hospital@test.com", jwtService.extractEmail(token));
        assertFalse(jwtService.extractExpiration(token).before(new java.util.Date()));
    }

    // TEST D: Missing JWT -> context remains unauthenticated.
    @Test
    public void testD_MissingJwtLeavesContextUnauthenticated() throws Exception {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService);
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        HttpServletResponse response = Mockito.mock(HttpServletResponse.class);
        FilterChain filterChain = Mockito.mock(FilterChain.class);

        when(request.getHeader("Authorization")).thenReturn(null);

        filter.doFilter(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication(),
                "SecurityContext must be unauthenticated when no token is provided");
        verify(filterChain, times(1)).doFilter(request, response);
    }

    // TEST E: Invalid JWT -> fails validation and does not authenticate.
    @Test
    public void testE_InvalidJwtFailsValidation() throws Exception {
        String invalidToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.invalid.signature";

        assertFalse(jwtService.validateToken(invalidToken));

        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService);
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        HttpServletResponse response = Mockito.mock(HttpServletResponse.class);
        FilterChain filterChain = Mockito.mock(FilterChain.class);

        when(request.getHeader("Authorization")).thenReturn("Bearer " + invalidToken);

        filter.doFilter(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication(),
                "SecurityContext must be unauthenticated for invalid token");
        verify(filterChain, times(1)).doFilter(request, response);
    }

    // TEST F: Expired JWT -> fails validation and does not authenticate.
    @Test
    public void testF_ExpiredJwtFailsValidation() throws Exception {
        // Generate a token expired 5 seconds ago (-5000 ms)
        String expiredToken = jwtService.generateTokenWithExpiration("d_123", "donor", "donor@test.com", -5000L);

        assertFalse(jwtService.validateToken(expiredToken), "Expired token must fail validation");

        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService);
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        HttpServletResponse response = Mockito.mock(HttpServletResponse.class);
        FilterChain filterChain = Mockito.mock(FilterChain.class);

        when(request.getHeader("Authorization")).thenReturn("Bearer " + expiredToken);

        filter.doFilter(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication(),
                "SecurityContext must be unauthenticated for expired token");
        verify(filterChain, times(1)).doFilter(request, response);
    }

    // TEST G: JWT contains correct userId and role.
    @Test
    public void testG_JwtContainsCorrectUserIdAndRole() {
        String token = jwtService.generateToken("b_456", "bloodbank", "bank@redcross.org");

        assertEquals("b_456", jwtService.extractUserId(token));
        assertEquals("bloodbank", jwtService.extractRole(token));
        assertEquals("bank@redcross.org", jwtService.extractEmail(token));
    }

    // TEST H: JWT cannot be altered without signature validation failing.
    @Test
    public void testH_TamperedJwtFailsSignatureValidation() {
        String validToken = jwtService.generateToken("d_100", "donor", "donor@safe.com");
        String[] parts = validToken.split("\\.");
        assertEquals(3, parts.length);

        // Tamper with payload (middle part)
        String tamperedToken = parts[0] + ".eyJzdWIiOiJkXzk5OSIsInJvbGUiOiJhZG1pbiJ9." + parts[2];

        assertFalse(jwtService.validateToken(tamperedToken), "Tampered JWT must fail signature verification");
    }

    // TEST: Signup also returns JWT token immediately for seamless session
    @Test
    public void testSignupReturnsJwtToken() {
        when(userRepository.existsByEmail(any())).thenReturn(false);

        SignupRequest req = new SignupRequest();
        req.setRole("donor");
        req.setName("New Donor");
        req.setEmail("newdonor@test.com");
        req.setPassword("Password123");
        req.setPhone("9123456780");
        req.setAge(24);
        req.setBloodGroup("A+");
        req.setCity("Mumbai");

        SignupResponse res = authService.registerUser(req);

        assertNotNull(res);
        assertNotNull(res.getToken(), "SignupResponse must include JWT token");
        assertTrue(jwtService.validateToken(res.getToken()));
        assertEquals(res.getId(), jwtService.extractUserId(res.getToken()));
        assertEquals("donor", jwtService.extractRole(res.getToken()));
    }

    // TEST: Filter correctly sets SecurityContext Authentication with ROLE_
    @Test
    public void testFilterSetsAuthenticationInSecurityContext() throws Exception {
        String token = jwtService.generateToken("h_777", "hospital", "apollo@hospital.com");

        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService);
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        HttpServletResponse response = Mockito.mock(HttpServletResponse.class);
        FilterChain filterChain = Mockito.mock(FilterChain.class);

        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);

        filter.doFilter(request, response, filterChain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth, "Authentication should be set in SecurityContext");
        assertEquals("h_777", auth.getPrincipal());
        assertTrue(auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_HOSPITAL")));
        verify(filterChain, times(1)).doFilter(request, response);
    }

    // TEST: Missing JWT secret fails validation and throws IllegalStateException
    @Test
    public void testMissingJwtSecretThrowsException() {
        JwtService unconfiguredJwtService = new JwtService(null, 604800000L);
        assertThrows(IllegalStateException.class, () -> unconfiguredJwtService.validateConfiguration(),
                "JwtService must throw IllegalStateException when secret is null");
        assertThrows(IllegalStateException.class, () -> unconfiguredJwtService.generateToken("u1", "donor", "u1@test.com"),
                "JwtService must throw IllegalStateException when generating token with null secret");
    }

    // TEST: Blank/Empty JWT secret fails validation and throws IllegalStateException
    @Test
    public void testBlankJwtSecretThrowsException() {
        JwtService blankJwtService = new JwtService("   ", 604800000L);
        assertThrows(IllegalStateException.class, () -> blankJwtService.validateConfiguration(),
                "JwtService must throw IllegalStateException when secret is blank");
        assertThrows(IllegalStateException.class, () -> blankJwtService.generateToken("u1", "donor", "u1@test.com"),
                "JwtService must throw IllegalStateException when generating token with blank secret");
    }
}
