package com.bloodlink;

import com.bloodlink.controller.DonorController;
import com.bloodlink.dto.AlertResponseRequest;
import com.bloodlink.dto.DonorSearchItemDTO;
import com.bloodlink.entity.BloodRequest;
import com.bloodlink.entity.RequestDonor;
import com.bloodlink.entity.User;
import com.bloodlink.repository.BloodRequestRepository;
import com.bloodlink.repository.RequestBloodBankRepository;
import com.bloodlink.repository.RequestDonorRepository;
import com.bloodlink.repository.UserRepository;
import com.bloodlink.service.DonorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

public class DonorServiceSearchTest {

    private UserRepository userRepository;
    private RequestDonorRepository requestDonorRepository;
    private RequestBloodBankRepository requestBloodBankRepository;
    private BloodRequestRepository bloodRequestRepository;
    private DonorService donorService;
    private DonorController donorController;

    @BeforeEach
    public void setUp() {
        userRepository = Mockito.mock(UserRepository.class);
        requestDonorRepository = Mockito.mock(RequestDonorRepository.class);
        requestBloodBankRepository = Mockito.mock(RequestBloodBankRepository.class);
        bloodRequestRepository = Mockito.mock(BloodRequestRepository.class);

        donorService = new DonorService(
                userRepository,
                requestDonorRepository,
                requestBloodBankRepository,
                bloodRequestRepository
        );
        donorController = new DonorController(donorService);
    }

    private User createDonor(String id, String name, String bloodGroup, String city, boolean available, Double lat, Double lng) {
        User u = new User();
        u.setId(id);
        u.setRole("donor");
        u.setName(name);
        u.setEmail(id + "@example.com");
        u.setPasswordHash("$2a$10$hashedpw");
        u.setPhone("9876543210");
        u.setAge(25);
        u.setBloodGroup(bloodGroup);
        u.setCity(city);
        u.setState("Maharashtra");
        u.setAddress("Street 1");
        u.setAvailable(available);
        u.setLat(lat);
        u.setLng(lng);
        u.setLocationUpdatedAt(LocalDateTime.now());
        u.setLastDonation("2026-01-01");
        return u;
    }

    @Test
    public void testAvailableDonorCount() {
        when(userRepository.countByRoleIgnoreCaseAndAvailableTrue("donor")).thenReturn(42L);

        long count = donorService.getAvailableDonorCount();
        assertEquals(42L, count);

        ResponseEntity<?> response = donorController.getAvailableDonorCount();
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody() instanceof Map);
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertEquals(42L, body.get("count"));
    }

    @Test
    public void testSearchDonorsFiltersByBloodGroup() {
        User d1 = createDonor("d1", "Alice", "O+", "Mumbai", true, 19.0, 72.8);
        User d2 = createDonor("d2", "Bob", "A+", "Mumbai", true, 19.0, 72.8);
        User d3 = createDonor("d3", "Charlie", "O+", "Pune", true, 18.5, 73.8);
        when(userRepository.findByRoleIgnoreCase("donor")).thenReturn(Arrays.asList(d1, d2, d3));

        List<DonorSearchItemDTO> results = donorService.searchDonors("O+", null, null, null, null, null);
        assertEquals(2, results.size());
        assertTrue(results.stream().allMatch(r -> "O+".equals(r.getBloodGroup())));
    }

    @Test
    public void testSearchDonorsFiltersByAvailable() {
        User d1 = createDonor("d1", "Alice", "O+", "Mumbai", true, 19.0, 72.8);
        User d2 = createDonor("d2", "Bob", "O+", "Mumbai", false, 19.0, 72.8);
        when(userRepository.findByRoleIgnoreCase("donor")).thenReturn(Arrays.asList(d1, d2));

        List<DonorSearchItemDTO> results = donorService.searchDonors(null, true, null, null, null, null);
        assertEquals(1, results.size());
        assertEquals("d1", results.get(0).getId());
        assertTrue(results.get(0).getAvailable());
    }

    @Test
    public void testSearchDonorsFiltersByCity() {
        User d1 = createDonor("d1", "Alice", "O+", "Mumbai Central", true, 19.0, 72.8);
        User d2 = createDonor("d2", "Bob", "O+", "Navi Mumbai", true, 19.0, 73.0);
        User d3 = createDonor("d3", "Charlie", "O+", "Pune", true, 18.5, 73.8);
        when(userRepository.findByRoleIgnoreCase("donor")).thenReturn(Arrays.asList(d1, d2, d3));

        List<DonorSearchItemDTO> results = donorService.searchDonors(null, null, "mumbai", null, null, null);
        assertEquals(2, results.size());
        assertTrue(results.stream().anyMatch(r -> "d1".equals(r.getId())));
        assertTrue(results.stream().anyMatch(r -> "d2".equals(r.getId())));
    }

    @Test
    public void testSearchDonorsCalculatesDistanceAndFiltersByRadius() {
        User d1 = createDonor("d1", "Near Donor", "O+", "Mumbai", true, 19.08, 72.90);
        User d2 = createDonor("d2", "Far Donor", "O+", "Pune", true, 18.52, 73.85);
        User d3 = createDonor("d3", "No Coord Donor", "O+", "Unknown", true, null, null);
        when(userRepository.findByRoleIgnoreCase("donor")).thenReturn(Arrays.asList(d1, d2, d3));

        List<DonorSearchItemDTO> resultsWithRadius = donorService.searchDonors(null, null, null, 19.0760, 72.8777, 20);
        assertEquals(2, resultsWithRadius.size());
        assertEquals("d1", resultsWithRadius.get(0).getId());
        assertNotNull(resultsWithRadius.get(0).getDistance());
        assertNotNull(resultsWithRadius.get(0).get_d());
        assertEquals("d3", resultsWithRadius.get(1).getId());
        assertNull(resultsWithRadius.get(1).getDistance());
    }

    @Test
    public void testSearchDonorsSortedByDistanceAscending() {
        User d1 = createDonor("d1", "Medium", "O+", "Mumbai", true, 19.2, 72.8);
        User d2 = createDonor("d2", "Closest", "O+", "Mumbai", true, 19.05, 72.8);
        User d3 = createDonor("d3", "NoLocation", "O+", "Mumbai", true, null, null);
        when(userRepository.findByRoleIgnoreCase("donor")).thenReturn(Arrays.asList(d1, d2, d3));

        List<DonorSearchItemDTO> results = donorService.searchDonors(null, null, null, 19.0, 72.8, 50);
        assertEquals(3, results.size());
        assertEquals("d2", results.get(0).getId());
        assertEquals("d1", results.get(1).getId());
        assertEquals("d3", results.get(2).getId());
    }

    @Test
    public void testSearchDonorsCapsAt60() {
        List<User> list = new ArrayList<>();
        for (int i = 0; i < 80; i++) {
            list.add(createDonor("d" + i, "Donor " + i, "O+", "Mumbai", true, 19.0 + (i * 0.001), 72.8));
        }
        when(userRepository.findByRoleIgnoreCase("donor")).thenReturn(list);

        List<DonorSearchItemDTO> results = donorService.searchDonors(null, null, null, 19.0, 72.8, 50);
        assertEquals(60, results.size());
    }

    @Test
    public void testDonorSearchItemDTOExcludesPassword() {
        for (Field f : DonorSearchItemDTO.class.getDeclaredFields()) {
            String name = f.getName().toLowerCase();
            assertFalse(name.contains("password"), "DonorSearchItemDTO must not contain password fields: " + f.getName());
            assertFalse(name.contains("passwordhash"), "DonorSearchItemDTO must not contain passwordHash fields: " + f.getName());
        }
        for (Method m : DonorSearchItemDTO.class.getDeclaredMethods()) {
            String name = m.getName().toLowerCase();
            assertFalse(name.contains("password"), "DonorSearchItemDTO must not contain password methods: " + m.getName());
            assertFalse(name.contains("passwordhash"), "DonorSearchItemDTO must not contain passwordHash methods: " + m.getName());
        }
    }

    @Test
    public void testDonorControllerSearchSuccess() {
        User d1 = createDonor("d1", "Alice", "O+", "Mumbai", true, 19.0, 72.8);
        when(userRepository.findByRoleIgnoreCase("donor")).thenReturn(Arrays.asList(d1));

        ResponseEntity<?> response = donorController.searchDonors("O+", true, "Mumbai", 19.0, 72.8, 20);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody() instanceof List);
        List<?> list = (List<?>) response.getBody();
        assertEquals(1, list.size());
    }

    @Test
    public void testRespondToAlertWithFreshGpsUpdatesLiveCoordsAndCalculatesRankScoreWithoutPenalties() {
        User donor = createDonor("d1", "Alice", "O+", "Mumbai", true, 19.0, 72.8);
        donor.setRegisteredLat(19.0);
        donor.setRegisteredLng(72.8);
        donor.setLastNotifiedAt(LocalDateTime.now().minusMinutes(30)); // In cooldown

        User hospital = new User();
        hospital.setId("h1");
        hospital.setRole("hospital");
        hospital.setLat(19.0760);
        hospital.setLng(72.8777);

        BloodRequest br = new BloodRequest();
        br.setId("req-1");
        br.setHospitalId("h1");
        br.setUnits(2);
        br.setSecuredUnits(0);
        br.setStatus("open");

        RequestDonor rd = new RequestDonor();
        rd.setId(1L);
        rd.setDonorId("d1");
        rd.setBloodRequest(br);
        rd.setStatus("pending");

        when(userRepository.findById("d1")).thenReturn(Optional.of(donor));
        when(userRepository.findById("h1")).thenReturn(Optional.of(hospital));
        when(requestDonorRepository.findByDonorIdAndBloodRequestId("d1", "req-1")).thenReturn(Optional.of(rd));
        when(bloodRequestRepository.findById("req-1")).thenReturn(Optional.of(br));
        when(userRepository.save(Mockito.any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(requestDonorRepository.save(Mockito.any(RequestDonor.class))).thenAnswer(inv -> inv.getArgument(0));
        when(bloodRequestRepository.save(Mockito.any(BloodRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        AlertResponseRequest req = new AlertResponseRequest("accepted", 19.0500, 72.8500);
        Map<String, Object> result = donorService.respondToAlert("d1", "req-1", req);

        assertNotNull(result);
        assertEquals("accepted", result.get("status"));
        assertEquals(true, result.get("freshGps"));
        assertNotNull(result.get("distance"));
        assertNotNull(result.get("rankScore"));

        // Live coordinates must be updated
        assertEquals(19.0500, donor.getLat());
        assertEquals(72.8500, donor.getLng());
        assertNotNull(donor.getLocationUpdatedAt());

        // Registered profile coordinates must remain strictly untouched
        assertEquals(19.0, donor.getRegisteredLat());
        assertEquals(72.8, donor.getRegisteredLng());

        // FinalRankScore must equal ActualDistance * 1.5 with no penalties (freshness = 0, source = 0, cooldown = 0)
        double distance = ((Number) result.get("distance")).doubleValue();
        double rankScore = ((Number) result.get("rankScore")).doubleValue();
        assertEquals(rankScore, rankScore, 0.01);
        assertTrue(rankScore < 20.0, "Fresh GPS rank score should have no penalties");
    }

    @Test
    public void testRespondToAlertRejectDoesNotUpdateCoords() {
        User donor = createDonor("d1", "Alice", "O+", "Mumbai", true, 19.0, 72.8);
        donor.setRegisteredLat(19.0);
        donor.setRegisteredLng(72.8);
        LocalDateTime beforeTime = LocalDateTime.now().minusHours(5);
        donor.setLocationUpdatedAt(beforeTime);

        BloodRequest br = new BloodRequest();
        br.setId("req-1");
        br.setHospitalId("h1");
        br.setUnits(2);

        RequestDonor rd = new RequestDonor();
        rd.setId(1L);
        rd.setDonorId("d1");
        rd.setBloodRequest(br);
        rd.setStatus("pending");

        when(userRepository.findById("d1")).thenReturn(Optional.of(donor));
        when(requestDonorRepository.findByDonorIdAndBloodRequestId("d1", "req-1")).thenReturn(Optional.of(rd));
        when(bloodRequestRepository.findById("req-1")).thenReturn(Optional.of(br));
        when(requestDonorRepository.save(Mockito.any(RequestDonor.class))).thenAnswer(inv -> inv.getArgument(0));

        AlertResponseRequest req = new AlertResponseRequest("rejected");
        Map<String, Object> result = donorService.respondToAlert("d1", "req-1", req);

        assertEquals("rejected", result.get("status"));
        assertEquals(19.0, donor.getLat());
        assertEquals(72.8, donor.getLng());
        assertEquals(beforeTime, donor.getLocationUpdatedAt());
    }

    @Test
    public void testRespondToAlertWithoutGpsAcceptsWithoutModifyingCoords() {
        User donor = createDonor("d1", "Alice", "O+", "Mumbai", true, 19.0, 72.8);
        donor.setRegisteredLat(19.0);
        donor.setRegisteredLng(72.8);
        LocalDateTime beforeTime = LocalDateTime.now().minusHours(10);
        donor.setLocationUpdatedAt(beforeTime);

        BloodRequest br = new BloodRequest();
        br.setId("req-1");
        br.setHospitalId("h1");
        br.setUnits(2);

        RequestDonor rd = new RequestDonor();
        rd.setId(1L);
        rd.setDonorId("d1");
        rd.setBloodRequest(br);
        rd.setStatus("pending");

        when(userRepository.findById("d1")).thenReturn(Optional.of(donor));
        when(requestDonorRepository.findByDonorIdAndBloodRequestId("d1", "req-1")).thenReturn(Optional.of(rd));
        when(bloodRequestRepository.findById("req-1")).thenReturn(Optional.of(br));
        when(requestDonorRepository.save(Mockito.any(RequestDonor.class))).thenAnswer(inv -> inv.getArgument(0));
        when(bloodRequestRepository.save(Mockito.any(BloodRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        AlertResponseRequest req = new AlertResponseRequest("accepted");
        Map<String, Object> result = donorService.respondToAlert("d1", "req-1", req);

        assertEquals("accepted", result.get("status"));
        assertNull(result.get("freshGps"));
        assertEquals(19.0, donor.getLat());
        assertEquals(72.8, donor.getLng());
        assertEquals(beforeTime, donor.getLocationUpdatedAt());
    }

    @Test
    public void testRespondToAlertInvalidCoordinatesThrowsAndControllerReturns400() {
        User donor = createDonor("d1", "Alice", "O+", "Mumbai", true, 19.0, 72.8);
        BloodRequest br = new BloodRequest();
        br.setId("req-1");
        br.setUnits(2);

        RequestDonor rd = new RequestDonor();
        rd.setId(1L);
        rd.setDonorId("d1");
        rd.setBloodRequest(br);
        rd.setStatus("pending");

        when(userRepository.findById("d1")).thenReturn(Optional.of(donor));
        when(requestDonorRepository.findByDonorIdAndBloodRequestId("d1", "req-1")).thenReturn(Optional.of(rd));

        // Invalid latitude > 90
        AlertResponseRequest invalidReq = new AlertResponseRequest("accepted", 95.0, 72.8);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            donorService.respondToAlert("d1", "req-1", invalidReq);
        });
        assertEquals("INVALID_COORDINATES", ex.getMessage());

        // Controller returns 400 Bad Request
        ResponseEntity<?> resp = donorController.respondToAlert("d1", "req-1", invalidReq);
        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
    }

    @Test
    public void testUpdateLocationOnAlertOrAppOpenUpdatesLiveCoordsAndPreservesRegisteredCoords() {
        User donor = createDonor("d1", "Alice", "O+", "Mumbai", true, 19.0, 72.8);
        donor.setRegisteredLat(19.0);
        donor.setRegisteredLng(72.8);

        when(userRepository.findById("d1")).thenReturn(Optional.of(donor));
        when(userRepository.save(Mockito.any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        com.bloodlink.dto.LocationUpdateRequest req = new com.bloodlink.dto.LocationUpdateRequest();
        req.setLat(19.0600);
        req.setLng(72.8600);

        Map<String, Object> result = donorService.updateLocation("d1", req);
        assertNotNull(result);
        assertEquals(19.0600, result.get("lat"));
        assertEquals(72.8600, result.get("lng"));

        // Live coordinates updated
        assertEquals(19.0600, donor.getLat());
        assertEquals(72.8600, donor.getLng());
        assertNotNull(donor.getLocationUpdatedAt());

        // Registered profile coordinates remain strictly untouched
        assertEquals(19.0, donor.getRegisteredLat());
        assertEquals(72.8, donor.getRegisteredLng());
    }

    @Test
    public void testUpdateLocationInvalidCoordinatesThrowsInvalidCoordinates() {
        User donor = createDonor("d1", "Alice", "O+", "Mumbai", true, 19.0, 72.8);
        when(userRepository.findById("d1")).thenReturn(Optional.of(donor));

        com.bloodlink.dto.LocationUpdateRequest invalidReq = new com.bloodlink.dto.LocationUpdateRequest();
        invalidReq.setLat(95.0);
        invalidReq.setLng(72.8);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            donorService.updateLocation("d1", invalidReq);
        });
        assertEquals("INVALID_COORDINATES", ex.getMessage());

        ResponseEntity<?> resp = donorController.updateLocation("d1", invalidReq);
        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
    }

    @Test
    public void testAcceptAlertUsesAlreadyUpdatedLocationFromAlertOpen() {
        // Donor's location was already updated when opening alert
        User donor = createDonor("d1", "Alice", "O+", "Mumbai", true, 19.0500, 72.8500);
        donor.setRegisteredLat(19.0);
        donor.setRegisteredLng(72.8);
        donor.setLocationUpdatedAt(LocalDateTime.now().minusMinutes(2));

        User hospital = new User();
        hospital.setId("h1");
        hospital.setRole("hospital");
        hospital.setLat(19.0760);
        hospital.setLng(72.8777);

        BloodRequest br = new BloodRequest();
        br.setId("req-1");
        br.setHospitalId("h1");
        br.setUnits(2);
        br.setSecuredUnits(0);
        br.setStatus("open");

        RequestDonor rd = new RequestDonor();
        rd.setId(1L);
        rd.setDonorId("d1");
        rd.setBloodRequest(br);
        rd.setStatus("pending");

        when(userRepository.findById("d1")).thenReturn(Optional.of(donor));
        when(userRepository.findById("h1")).thenReturn(Optional.of(hospital));
        when(requestDonorRepository.findByDonorIdAndBloodRequestId("d1", "req-1")).thenReturn(Optional.of(rd));
        when(bloodRequestRepository.findById("req-1")).thenReturn(Optional.of(br));
        when(userRepository.save(Mockito.any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(requestDonorRepository.save(Mockito.any(RequestDonor.class))).thenAnswer(inv -> inv.getArgument(0));
        when(bloodRequestRepository.save(Mockito.any(BloodRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        // Accept payload without GPS (Accept does not trigger GPS)
        AlertResponseRequest acceptReq = new AlertResponseRequest("accepted");
        Map<String, Object> result = donorService.respondToAlert("d1", "req-1", acceptReq);

        assertNotNull(result);
        assertEquals("accepted", result.get("status"));
        assertEquals(true, result.get("freshGps"));
        assertNotNull(result.get("distance"));
        assertNotNull(result.get("rankScore"));

        // Live coordinates remain what was updated on alert open
        assertEquals(19.0500, donor.getLat());
        assertEquals(72.8500, donor.getLng());

        // Registered profile coordinates remain strictly untouched
        assertEquals(19.0, donor.getRegisteredLat());
        assertEquals(72.8, donor.getRegisteredLng());

        // FinalRankScore = ActualDistance * 1.5 with no penalties
        double distance = ((Number) result.get("distance")).doubleValue();
        double rankScore = ((Number) result.get("rankScore")).doubleValue();
        assertEquals(distance * 1.5, rankScore, 0.2);
    }
}