package com.bloodlink;

import com.bloodlink.controller.DonorController;
import com.bloodlink.dto.CreateDonationRequest;
import com.bloodlink.dto.DonationHistoryDTO;
import com.bloodlink.entity.DonationHistory;
import com.bloodlink.entity.User;
import com.bloodlink.repository.DonationHistoryRepository;
import com.bloodlink.repository.UserRepository;
import com.bloodlink.service.DonationHistoryService;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class DonationHistoryServiceTest {

    private DonationHistoryRepository donationHistoryRepository;
    private UserRepository userRepository;
    private DonationHistoryService donationHistoryService;
    private DonorService donorService;
    private DonorController donorController;

    @BeforeEach
    public void setUp() {
        donationHistoryRepository = Mockito.mock(DonationHistoryRepository.class);
        userRepository = Mockito.mock(UserRepository.class);
        donorService = Mockito.mock(DonorService.class);

        donationHistoryService = new DonationHistoryService(donationHistoryRepository, userRepository);
        donorController = new DonorController(donorService, donationHistoryService);
    }

    private User createDonor(String id, String name, String lastDonation) {
        User u = new User();
        u.setId(id);
        u.setRole("donor");
        u.setName(name);
        u.setEmail(id + "@example.com");
        u.setPasswordHash("$2a$10$hashedsecret");
        u.setBloodGroup("O+");
        u.setLastDonation(lastDonation);
        return u;
    }

    private DonationHistory createHistory(String id, String donorId, String date, String loc, int units, String notes, LocalDateTime createdAt) {
        DonationHistory h = new DonationHistory();
        h.setId(id);
        h.setDonorId(donorId);
        h.setDate(date);
        h.setLocation(loc);
        h.setUnits(units);
        h.setNotes(notes);
        h.setCreatedAt(createdAt);
        return h;
    }

    @Test
    public void testGetDonationHistorySuccess() {
        User donor = createDonor("d1", "John", "2026-01-15");
        when(userRepository.findById("d1")).thenReturn(Optional.of(donor));

        DonationHistory h1 = createHistory("dh1", "d1", "2026-01-15", "City Hospital", 1, "Routine donation", LocalDateTime.of(2026, 1, 15, 10, 0));
        DonationHistory h2 = createHistory("dh2", "d1", "2025-10-10", "Red Cross Camp", 2, null, LocalDateTime.of(2025, 10, 10, 11, 0));
        when(donationHistoryRepository.findByDonorIdOrderByDateDesc("d1")).thenReturn(Arrays.asList(h1, h2));

        List<DonationHistoryDTO> result = donationHistoryService.getDonationHistory("d1");

        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("dh1", result.get(0).getId());
        assertEquals("d1", result.get(0).getDonorId());
        assertEquals("2026-01-15", result.get(0).getDate());
        assertEquals("City Hospital", result.get(0).getLocation());
        assertEquals(1, result.get(0).getUnits());
        assertEquals("Routine donation", result.get(0).getNotes());
        assertNotNull(result.get(0).getCreatedAt());
    }

    @Test
    public void testGetDonationHistoryUnknownDonorThrowsNotFound() {
        when(userRepository.findById("unknown_donor")).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            donationHistoryService.getDonationHistory("unknown_donor");
        });
        assertEquals("DONOR_NOT_FOUND", ex.getMessage());
    }

    @Test
    public void testGetDonationHistoryNonDonorRoleThrowsNotADonor() {
        User hospital = new User();
        hospital.setId("h1");
        hospital.setRole("hospital");
        when(userRepository.findById("h1")).thenReturn(Optional.of(hospital));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            donationHistoryService.getDonationHistory("h1");
        });
        assertEquals("NOT_A_DONOR", ex.getMessage());
    }

    @Test
    public void testGetDonationHistoryEmptyReturnsEmptyList() {
        User donor = createDonor("d1", "John", null);
        when(userRepository.findById("d1")).thenReturn(Optional.of(donor));
        when(donationHistoryRepository.findByDonorIdOrderByDateDesc("d1")).thenReturn(new ArrayList<>());

        List<DonationHistoryDTO> result = donationHistoryService.getDonationHistory("d1");
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testGetDonationHistoryPreservesOrdering() {
        User donor = createDonor("d1", "John", null);
        when(userRepository.findById("d1")).thenReturn(Optional.of(donor));

        DonationHistory hOld = createHistory("dh1", "d1", "2025-01-01", "Loc A", 1, null, LocalDateTime.of(2025, 1, 1, 10, 0));
        DonationHistory hMid = createHistory("dh2", "d1", "2025-06-01", "Loc B", 1, null, LocalDateTime.of(2025, 6, 1, 10, 0));
        DonationHistory hNew = createHistory("dh3", "d1", "2026-02-01", "Loc C", 1, null, LocalDateTime.of(2026, 2, 1, 10, 0));
        when(donationHistoryRepository.findByDonorIdOrderByDateDesc("d1")).thenReturn(Arrays.asList(hMid, hOld, hNew));

        List<DonationHistoryDTO> result = donationHistoryService.getDonationHistory("d1");
        assertEquals(3, result.size());
        assertEquals("dh3", result.get(0).getId()); // 2026-02-01
        assertEquals("dh2", result.get(1).getId()); // 2025-06-01
        assertEquals("dh1", result.get(2).getId()); // 2025-01-01
    }

    @Test
    public void testCreateDonationSuccessAndUpdatesUserLastDonation() {
        User donor = createDonor("d1", "John", "2025-10-01");
        when(userRepository.findById("d1")).thenReturn(Optional.of(donor));

        CreateDonationRequest req = new CreateDonationRequest("2026-02-15", "Apollo Hospital", 1, "Successful blood donation");
        DonationHistoryDTO created = donationHistoryService.createDonation("d1", req);

        assertNotNull(created);
        assertEquals("d1", created.getDonorId());
        assertEquals("2026-02-15", created.getDate());
        assertEquals("Apollo Hospital", created.getLocation());
        assertEquals(1, created.getUnits());
        assertEquals("Successful blood donation", created.getNotes());
        assertNotNull(created.getId());
        assertNotNull(created.getCreatedAt());

        verify(donationHistoryRepository, times(1)).save(any(DonationHistory.class));
        assertEquals("2026-02-15", donor.getLastDonation());
        verify(userRepository, times(1)).save(donor);
    }

    @Test
    public void testCreateDonationWithOlderDateDoesNotRegressLastDonation() {
        User donor = createDonor("d1", "John", "2026-03-01");
        when(userRepository.findById("d1")).thenReturn(Optional.of(donor));

        CreateDonationRequest req = new CreateDonationRequest("2025-12-01", "Apollo Hospital", 1, null);
        donationHistoryService.createDonation("d1", req);

        verify(donationHistoryRepository, times(1)).save(any(DonationHistory.class));
        assertEquals("2026-03-01", donor.getLastDonation());
        verify(userRepository, never()).save(donor);
    }

    @Test
    public void testCreateDonationRejectsInvalidInputs() {
        User donor = createDonor("d1", "John", null);
        when(userRepository.findById("d1")).thenReturn(Optional.of(donor));

        assertThrows(IllegalArgumentException.class, () -> {
            donationHistoryService.createDonation("d1", null);
        });

        assertThrows(IllegalArgumentException.class, () -> {
            donationHistoryService.createDonation("d1", new CreateDonationRequest(null, "Apollo Hospital", 1, null));
        });

        assertThrows(IllegalArgumentException.class, () -> {
            donationHistoryService.createDonation("d1", new CreateDonationRequest("   ", "Apollo Hospital", 1, null));
        });

        assertThrows(IllegalArgumentException.class, () -> {
            donationHistoryService.createDonation("d1", new CreateDonationRequest("2026-01-01", "", 1, null));
        });
    }

    @Test
    public void testDonationHistoryDTODoesNotExposePassword() {
        for (Field f : DonationHistoryDTO.class.getDeclaredFields()) {
            String name = f.getName().toLowerCase();
            assertFalse(name.contains("password"), "DonationHistoryDTO must not contain password field: " + f.getName());
            assertFalse(name.contains("passwordhash"), "DonationHistoryDTO must not contain passwordHash field: " + f.getName());
        }
        for (Method m : DonationHistoryDTO.class.getDeclaredMethods()) {
            String name = m.getName().toLowerCase();
            assertFalse(name.contains("password"), "DonationHistoryDTO must not contain password method: " + m.getName());
            assertFalse(name.contains("passwordhash"), "DonationHistoryDTO must not contain passwordHash method: " + m.getName());
        }
    }

    @Test
    public void testDonorControllerHistoryEndpoints() {
        User donor = createDonor("d1", "John", "2026-01-01");
        when(userRepository.findById("d1")).thenReturn(Optional.of(donor));
        DonationHistory h = createHistory("dh1", "d1", "2026-01-01", "City Hospital", 1, null, LocalDateTime.now());
        when(donationHistoryRepository.findByDonorIdOrderByDateDesc("d1")).thenReturn(Arrays.asList(h));

        // GET endpoint
        ResponseEntity<?> getResp = donorController.getDonationHistory("d1");
        assertEquals(HttpStatus.OK, getResp.getStatusCode());
        assertTrue(getResp.getBody() instanceof List);

        // POST endpoint
        CreateDonationRequest req = new CreateDonationRequest("2026-02-01", "General Hospital", 1, "Note");
        ResponseEntity<?> postResp = donorController.createDonation("d1", req);
        assertEquals(HttpStatus.CREATED, postResp.getStatusCode());
        assertTrue(postResp.getBody() instanceof DonationHistoryDTO);

        // 404 for unknown donor
        when(userRepository.findById("invalid")).thenReturn(Optional.empty());
        ResponseEntity<?> notFoundGet = donorController.getDonationHistory("invalid");
        assertEquals(HttpStatus.NOT_FOUND, notFoundGet.getStatusCode());

        ResponseEntity<?> notFoundPost = donorController.createDonation("invalid", req);
        assertEquals(HttpStatus.NOT_FOUND, notFoundPost.getStatusCode());
    }
}