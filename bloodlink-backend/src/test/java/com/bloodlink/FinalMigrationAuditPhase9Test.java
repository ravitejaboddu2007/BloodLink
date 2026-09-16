package com.bloodlink;

import com.bloodlink.dto.*;
import com.bloodlink.entity.*;
import com.bloodlink.repository.*;
import com.bloodlink.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class FinalMigrationAuditPhase9Test {

    private UserRepository userRepository;
    private BloodRequestRepository bloodRequestRepository;
    private RequestDonorRepository requestDonorRepository;
    private RequestBloodBankRepository requestBloodBankRepository;
    private InventoryRepository inventoryRepository;
    private DonationHistoryRepository donationHistoryRepository;
    private ReviewRepository reviewRepository;
    private ObjectMapper objectMapper;

    private UserService userService;
    private AuthService authService;
    private BloodRequestService bloodRequestService;
    private BloodBankRequestService bloodBankRequestService;
    private InventoryService inventoryService;
    private DonationHistoryService donationHistoryService;
    private ReviewService reviewService;

    @BeforeEach
    public void setUp() {
        userRepository = Mockito.mock(UserRepository.class);
        bloodRequestRepository = Mockito.mock(BloodRequestRepository.class);
        requestDonorRepository = Mockito.mock(RequestDonorRepository.class);
        requestBloodBankRepository = Mockito.mock(RequestBloodBankRepository.class);
        inventoryRepository = Mockito.mock(InventoryRepository.class);
        donationHistoryRepository = Mockito.mock(DonationHistoryRepository.class);
        reviewRepository = Mockito.mock(ReviewRepository.class);
        objectMapper = new ObjectMapper();

        userService = new UserService(userRepository);
        authService = new AuthService(userRepository, inventoryRepository);
        bloodRequestService = new BloodRequestService(
                bloodRequestRepository,
                requestDonorRepository,
                requestBloodBankRepository,
                donationHistoryRepository,
                userRepository
        );
        bloodBankRequestService = new BloodBankRequestService(
                requestBloodBankRepository,
                bloodRequestRepository,
                requestDonorRepository,
                userRepository,
                inventoryRepository,
                objectMapper
        );
        inventoryService = new InventoryService(inventoryRepository, userRepository, objectMapper);
        donationHistoryService = new DonationHistoryService(donationHistoryRepository, userRepository);
        reviewService = new ReviewService(reviewRepository, userRepository);
    }

    @Test
    public void testUsersSourceOfTruthInMySQL() {
        User donor = new User();
        donor.setId("d100");
        donor.setName("Alice Donor");
        donor.setEmail("alice@test.com");
        donor.setRole("donor");
        donor.setBloodGroup("O+");
        donor.setCity("Metropolis");
        donor.setAvailable(true);

        when(userRepository.findById("d100")).thenReturn(Optional.of(donor));
        when(userRepository.existsById("d100")).thenReturn(true);
        when(userRepository.countByRoleIgnoreCase("donor")).thenReturn(15L);
        when(userRepository.countByRoleIgnoreCase("hospital")).thenReturn(5L);
        when(userRepository.countByRoleIgnoreCase("bloodbank")).thenReturn(4L);
        when(userRepository.count()).thenReturn(24L);

        UserProfileDTO profile = userService.getUserProfile("d100");
        assertNotNull(profile);
        assertEquals("Alice Donor", profile.getName());
        assertEquals("O+", profile.getBloodGroup());
        assertTrue(userService.existsUser("d100"));

        Map<String, Long> stats = userService.getUserStats();
        assertEquals(15L, stats.get("donors"));
        assertEquals(5L, stats.get("hospitals"));
        assertEquals(4L, stats.get("bloodbanks"));
        assertEquals(24L, stats.get("total"));
    }

    @Test
    public void testRequestsSourceOfTruthInMySQL() {
        User hospital = new User();
        hospital.setId("h100");
        hospital.setName("City General Hospital");
        hospital.setRole("hospital");

        BloodRequest req = new BloodRequest();
        req.setId("req-999");
        req.setHospitalId("h100");
        req.setPatientName("John Patient");
        req.setBloodGroup("A+");
        req.setUnits(3);
        req.setStatus("OPEN");
        req.setUrgency("URGENT");
        req.setContact("9999999999");
        req.setRadius(10);
        req.setRequestType("EMERGENCY");

        when(userRepository.findById("h100")).thenReturn(Optional.of(hospital));
        when(bloodRequestRepository.findById("req-999")).thenReturn(Optional.of(req));
        when(bloodRequestRepository.count()).thenReturn(42L);

        HospitalRequestDTO dto = bloodRequestService.getRequestById("req-999");
        assertNotNull(dto);
        assertEquals("John Patient", dto.getPatientName());
        assertEquals("A+", dto.getBloodGroup());
        assertEquals(42L, bloodRequestService.getTotalRequestCount());
    }

    @Test
    public void testInventorySourceOfTruthInMySQL() {
        User bloodBank = new User();
        bloodBank.setId("bb100");
        bloodBank.setName("Metro Central Blood Bank");
        bloodBank.setRole("bloodbank");

        Inventory inv = new Inventory();
        inv.setId(1L);
        inv.setBloodBankId("bb100");
        inv.setStockBPos(12);

        when(userRepository.findById("bb100")).thenReturn(Optional.of(bloodBank));
        when(inventoryRepository.findByBloodBankId("bb100")).thenReturn(Optional.of(inv));

        int stock = inventoryService.getStockByGroup("bb100", "B+");
        assertEquals(12, stock);

        InventoryDTO invDTO = inventoryService.getInventory("bb100");
        assertNotNull(invDTO);
        assertEquals(12, invDTO.getStock().get("B+"));
    }

    @Test
    public void testDonationHistorySourceOfTruthInMySQL() {
        User donor = new User();
        donor.setId("d100");
        donor.setName("Alice Donor");
        donor.setRole("donor");

        DonationHistory h = new DonationHistory();
        h.setId("hist-1");
        h.setDonorId("d100");
        h.setLocation("City Hospital");
        h.setUnits(1);
        h.setDate("2026-01-15");
        h.setCreatedAt(LocalDateTime.now());

        when(userRepository.findById("d100")).thenReturn(Optional.of(donor));
        when(donationHistoryRepository.findByDonorIdOrderByDateDesc("d100"))
                .thenReturn(Collections.singletonList(h));

        List<DonationHistoryDTO> list = donationHistoryService.getDonationHistory("d100");
        assertEquals(1, list.size());
        assertEquals("hist-1", list.get(0).getId());
        assertEquals("City Hospital", list.get(0).getLocation());
    }

    @Test
    public void testReviewsSourceOfTruthInMySQL() {
        User reviewer = new User();
        reviewer.setId("u100");
        reviewer.setName("Reviewer Jane");
        reviewer.setRole("donor");

        Review r = new Review();
        r.setId("rev-1");
        r.setUserId("u100");
        r.setRating(5);
        r.setText("Great coordination platform!");
        r.setCreatedAt(LocalDateTime.now());

        when(reviewRepository.findAllByOrderByCreatedAtDesc()).thenReturn(Collections.singletonList(r));
        when(reviewRepository.findByUserId("u100")).thenReturn(Optional.of(r));

        List<ReviewDTO> allReviews = reviewService.getAllReviews();
        assertEquals(1, allReviews.size());
        assertEquals("Great coordination platform!", allReviews.get(0).getText());
        assertEquals(5, allReviews.get(0).getRating());

        Optional<ReviewDTO> userRev = reviewService.getReviewByUserId("u100");
        assertTrue(userRev.isPresent());
        assertEquals("u100", userRev.get().getUserId());
    }

    @Test
    public void testDonorMatchingMySQLBacking() {
        User hospital = new User();
        hospital.setId("h1");
        hospital.setRole("hospital");
        hospital.setLat(12.9716);
        hospital.setLng(77.5946);

        BloodRequest req = new BloodRequest();
        req.setId("req-1");
        req.setHospitalId("h1");
        req.setBloodGroup("O+");
        req.setUnits(2);
        req.setRadius(20);
        req.setStatus("OPEN");

        User d1 = new User();
        d1.setId("d1");
        d1.setName("Donor 1");
        d1.setRole("donor");
        d1.setBloodGroup("O+");
        d1.setAvailable(true);
        d1.setLat(12.9720);
        d1.setLng(77.5950);
        d1.setLocationUpdatedAt(LocalDateTime.now().minusMinutes(10));

        when(userRepository.findById("h1")).thenReturn(Optional.of(hospital));
        when(bloodRequestRepository.findById("req-1")).thenReturn(Optional.of(req));
        when(userRepository.findByRoleIgnoreCaseAndAvailableTrue("donor")).thenReturn(Collections.singletonList(d1));

        MatchDonorsResponseDTO result = bloodRequestService.matchDonorsForRequest("req-1");
        assertNotNull(result);
        assertEquals(1, result.getDirect().size());
        assertEquals("d1", result.getDirect().get(0).getId());
    }

    @Test
    public void testBloodBankMatchingMySQLBacking() {
        User hospital = new User();
        hospital.setId("h1");
        hospital.setRole("hospital");
        hospital.setLat(12.9716);
        hospital.setLng(77.5946);

        BloodRequest req = new BloodRequest();
        req.setId("req-1");
        req.setHospitalId("h1");
        req.setBloodGroup("A+");
        req.setUnits(4);
        req.setRadius(25);
        req.setStatus("OPEN");

        User bb = new User();
        bb.setId("bb1");
        bb.setName("City Blood Bank");
        bb.setRole("bloodbank");
        bb.setLat(12.9730);
        bb.setLng(77.5960);

        Inventory inv = new Inventory();
        inv.setBloodBankId("bb1");
        inv.setStockAPos(10);

        when(userRepository.findById("h1")).thenReturn(Optional.of(hospital));
        when(bloodRequestRepository.findById("req-1")).thenReturn(Optional.of(req));
        when(userRepository.findByRoleIgnoreCaseAndLatIsNotNullAndLngIsNotNull("bloodbank")).thenReturn(Collections.singletonList(bb));
        when(inventoryRepository.findByBloodBankId("bb1")).thenReturn(Optional.of(inv));

        MatchBloodBanksResponseDTO result = bloodBankRequestService.matchBloodBanksForRequest("req-1");
        assertNotNull(result);
        assertEquals(1, result.getBloodBanks().size());
        assertEquals("bb1", result.getBloodBanks().get(0).getId());
        assertEquals("sufficient", result.getBloodBanks().get(0).getStatus().toLowerCase());
    }

    @Test
    public void testDTOSecurityAcrossAllDomains() {
        for (Class<?> dtoClass : Arrays.asList(
                UserProfileDTO.class,
                DonorSearchItemDTO.class,
                DonationHistoryDTO.class,
                ReviewDTO.class,
                HospitalRequestDTO.class,
                InventoryDTO.class,
                LoginResponse.class,
                SignupResponse.class
        )) {
            for (java.lang.reflect.Field field : dtoClass.getDeclaredFields()) {
                String fieldName = field.getName().toLowerCase();
                assertFalse(fieldName.contains("password") && !fieldName.contains("passwordreset"),
                        "Field " + field.getName() + " in DTO " + dtoClass.getSimpleName() + " must not contain password data");
            }
        }
    }
}
