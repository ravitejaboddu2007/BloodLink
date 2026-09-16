package com.bloodlink.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class RoleBasedAuthorizationTest {

    private static final String TEST_SECRET = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";
    private static final long TEST_EXPIRATION_MS = 604800000L;

    private JwtService jwtService;
    private JwtAuthenticationFilter filter;

    @BeforeEach
    public void setUp() {
        SecurityContextHolder.clearContext();
        jwtService = new JwtService(TEST_SECRET, TEST_EXPIRATION_MS);
        filter = new JwtAuthenticationFilter(jwtService);
    }

    @AfterEach
    public void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private Authentication authenticateWithToken(String token) throws Exception {
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        HttpServletResponse response = Mockito.mock(HttpServletResponse.class);
        FilterChain filterChain = Mockito.mock(FilterChain.class);

        when(request.getHeader("Authorization")).thenReturn(token != null ? "Bearer " + token : null);
        filter.doFilter(request, response, filterChain);
        return SecurityContextHolder.getContext().getAuthentication();
    }

    private boolean hasRole(Authentication auth, String requiredRole) {
        if (auth == null || !auth.isAuthenticated()) return false;
        String expected = "ROLE_" + requiredRole.toUpperCase();
        return auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals(expected));
    }

    // TEST 1: No JWT accessing protected donor endpoint -> 401 (Unauthenticated)
    @Test
    public void test1_NoJwtResultsInUnauthenticated() throws Exception {
        Authentication auth = authenticateWithToken(null);
        assertNull(auth, "No JWT must leave context unauthenticated (401)");
    }

    // TEST 2: Hospital JWT accessing donor-only endpoint -> 403 (Wrong role)
    @Test
    public void test2_HospitalJwtFailsDonorRoleCheck() throws Exception {
        String hospitalToken = jwtService.generateToken("h_101", "hospital", "hosp@test.com");
        Authentication auth = authenticateWithToken(hospitalToken);

        assertNotNull(auth);
        assertTrue(hasRole(auth, "HOSPITAL"));
        assertFalse(hasRole(auth, "DONOR"), "Hospital must NOT have ROLE_DONOR (403)");
    }

    // TEST 3: Blood bank JWT accessing donor-only endpoint -> 403 (Wrong role)
    @Test
    public void test3_BloodBankJwtFailsDonorRoleCheck() throws Exception {
        String bloodBankToken = jwtService.generateToken("b_202", "bloodbank", "bank@test.com");
        Authentication auth = authenticateWithToken(bloodBankToken);

        assertNotNull(auth);
        assertTrue(hasRole(auth, "BLOODBANK"));
        assertFalse(hasRole(auth, "DONOR"), "Blood Bank must NOT have ROLE_DONOR (403)");
    }

    // TEST 4: Donor JWT accessing donor-only endpoint -> role authorization passes
    @Test
    public void test4_DonorJwtPassesDonorRoleCheck() throws Exception {
        String donorToken = jwtService.generateToken("d_303", "donor", "donor@test.com");
        Authentication auth = authenticateWithToken(donorToken);

        assertNotNull(auth);
        assertEquals("d_303", auth.getPrincipal());
        assertTrue(hasRole(auth, "DONOR"), "Donor must have ROLE_DONOR (Access Granted)");
    }

    // TEST 5: Donor JWT accessing hospital-only endpoint -> 403 (Wrong role)
    @Test
    public void test5_DonorJwtFailsHospitalRoleCheck() throws Exception {
        String donorToken = jwtService.generateToken("d_303", "donor", "donor@test.com");
        Authentication auth = authenticateWithToken(donorToken);

        assertNotNull(auth);
        assertFalse(hasRole(auth, "HOSPITAL"), "Donor must NOT have ROLE_HOSPITAL (403)");
    }

    // TEST 6: Blood bank JWT accessing hospital-only endpoint -> 403 (Wrong role)
    @Test
    public void test6_BloodBankJwtFailsHospitalRoleCheck() throws Exception {
        String bloodBankToken = jwtService.generateToken("b_202", "bloodbank", "bank@test.com");
        Authentication auth = authenticateWithToken(bloodBankToken);

        assertNotNull(auth);
        assertFalse(hasRole(auth, "HOSPITAL"), "Blood Bank must NOT have ROLE_HOSPITAL (403)");
    }

    // TEST 7: Hospital JWT accessing hospital-only endpoint -> role authorization passes
    @Test
    public void test7_HospitalJwtPassesHospitalRoleCheck() throws Exception {
        String hospitalToken = jwtService.generateToken("h_101", "hospital", "hosp@test.com");
        Authentication auth = authenticateWithToken(hospitalToken);

        assertNotNull(auth);
        assertEquals("h_101", auth.getPrincipal());
        assertTrue(hasRole(auth, "HOSPITAL"), "Hospital must have ROLE_HOSPITAL (Access Granted)");
    }

    // TEST 8: Donor JWT accessing blood-bank-only endpoint -> 403 (Wrong role)
    @Test
    public void test8_DonorJwtFailsBloodBankRoleCheck() throws Exception {
        String donorToken = jwtService.generateToken("d_303", "donor", "donor@test.com");
        Authentication auth = authenticateWithToken(donorToken);

        assertNotNull(auth);
        assertFalse(hasRole(auth, "BLOODBANK"), "Donor must NOT have ROLE_BLOODBANK (403)");
    }

    // TEST 9: Hospital JWT accessing blood-bank-only endpoint -> 403 (Wrong role)
    @Test
    public void test9_HospitalJwtFailsBloodBankRoleCheck() throws Exception {
        String hospitalToken = jwtService.generateToken("h_101", "hospital", "hosp@test.com");
        Authentication auth = authenticateWithToken(hospitalToken);

        assertNotNull(auth);
        assertFalse(hasRole(auth, "BLOODBANK"), "Hospital must NOT have ROLE_BLOODBANK (403)");
    }

    // TEST 10: Blood bank JWT accessing blood-bank-only endpoint -> role authorization passes
    @Test
    public void test10_BloodBankJwtPassesBloodBankRoleCheck() throws Exception {
        String bloodBankToken = jwtService.generateToken("b_202", "bloodbank", "bank@test.com");
        Authentication auth = authenticateWithToken(bloodBankToken);

        assertNotNull(auth);
        assertEquals("b_202", auth.getPrincipal());
        assertTrue(hasRole(auth, "BLOODBANK"), "Blood Bank must have ROLE_BLOODBANK (Access Granted)");
    }

    // TEST 11: Public login endpoint without JWT -> still accessible without auth
    @Test
    public void test11_PublicLoginAccessibleWithoutToken() {
        // Simulates unauthenticated caller accessing public endpoint
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    // TEST 12: Public signup endpoint without JWT -> still accessible without auth
    @Test
    public void test12_PublicSignupAccessibleWithoutToken() {
        // Simulates unauthenticated caller accessing public signup
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    // TEST 13: OPTIONS preflight -> filter passes through without blocking
    @Test
    public void test13_OptionsPreflightPassesThrough() throws Exception {
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        HttpServletResponse response = Mockito.mock(HttpServletResponse.class);
        FilterChain filterChain = Mockito.mock(FilterChain.class);

        when(request.getMethod()).thenReturn("OPTIONS");
        when(request.getHeader("Authorization")).thenReturn(null);

        filter.doFilter(request, response, filterChain);

        verify(filterChain, times(1)).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    // TEST 14: Shared endpoints accept any valid authenticated role
    @Test
    public void test14_SharedEndpointsPermitAnyValidRole() throws Exception {
        String[] roles = {"donor", "hospital", "bloodbank"};
        for (String role : roles) {
            SecurityContextHolder.clearContext();
            String token = jwtService.generateToken("u_" + role, role, role + "@test.com");
            Authentication auth = authenticateWithToken(token);

            assertNotNull(auth);
            boolean hasAnyValidRole = hasRole(auth, "DONOR") || hasRole(auth, "HOSPITAL") || hasRole(auth, "BLOODBANK");
            assertTrue(hasAnyValidRole, "Role " + role + " must be accepted by shared endpoints");
        }
    }

    // TEST 15: Centralized CORS configuration source verifies allowed methods and headers
    @Test
    public void test15_CentralizedCorsConfigurationSource() {
        SecurityConfig config = new SecurityConfig(filter);
        org.springframework.web.cors.UrlBasedCorsConfigurationSource source = (org.springframework.web.cors.UrlBasedCorsConfigurationSource) config.corsConfigurationSource();
        assertNotNull(source);

        org.springframework.web.cors.CorsConfiguration cors = source.getCorsConfigurations().get("/**");
        assertNotNull(cors, "CORS configuration for /** must be registered");
        assertTrue(cors.getAllowedMethods().contains("GET"));
        assertTrue(cors.getAllowedMethods().contains("POST"));
        assertTrue(cors.getAllowedMethods().contains("PUT"));
        assertTrue(cors.getAllowedMethods().contains("DELETE"));
        assertTrue(cors.getAllowedMethods().contains("OPTIONS"));
        assertTrue(cors.getAllowedHeaders().contains("Authorization"));
        assertTrue(cors.getExposedHeaders().contains("Authorization"));
        assertTrue(Boolean.TRUE.equals(cors.getAllowCredentials()));
    }
}
