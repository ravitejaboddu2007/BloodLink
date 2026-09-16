package com.bloodlink;

import com.bloodlink.dto.MatchBloodBanksResponseDTO;
import com.bloodlink.dto.MatchedBloodBankItemDTO;
import com.bloodlink.entity.BloodRequest;
import com.bloodlink.entity.Inventory;
import com.bloodlink.entity.User;
import com.bloodlink.repository.BloodRequestRepository;
import com.bloodlink.repository.InventoryRepository;
import com.bloodlink.repository.RequestBloodBankRepository;
import com.bloodlink.repository.RequestDonorRepository;
import com.bloodlink.repository.UserRepository;
import com.bloodlink.service.BloodBankRequestService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

public class BloodBankRequestServiceMatchingTest {

    private RequestBloodBankRepository requestBloodBankRepository;
    private BloodRequestRepository bloodRequestRepository;
    private RequestDonorRepository requestDonorRepository;
    private UserRepository userRepository;
    private InventoryRepository inventoryRepository;
    private ObjectMapper objectMapper;
    private BloodBankRequestService bloodBankRequestService;

    @BeforeEach
    public void setUp() {
        requestBloodBankRepository = Mockito.mock(RequestBloodBankRepository.class);
        bloodRequestRepository = Mockito.mock(BloodRequestRepository.class);
        requestDonorRepository = Mockito.mock(RequestDonorRepository.class);
        userRepository = Mockito.mock(UserRepository.class);
        inventoryRepository = Mockito.mock(InventoryRepository.class);
        objectMapper = new ObjectMapper();

        bloodBankRequestService = new BloodBankRequestService(
                requestBloodBankRepository,
                bloodRequestRepository,
                requestDonorRepository,
                userRepository,
                inventoryRepository,
                objectMapper
        );
    }

    private User createHospital(String id, Double lat, Double lng) {
        User u = new User();
        u.setId(id);
        u.setName("Test Hospital");
        u.setRole("hospital");
        u.setLat(lat);
        u.setLng(lng);
        u.setCity("TestCity");
        u.setState("TestState");
        return u;
    }

    private User createBloodBank(String id, String name, Double lat, Double lng) {
        User u = new User();
        u.setId(id);
        u.setName(name);
        u.setRole("bloodbank");
        u.setRegistrationNumber("REG-" + id);
        u.setPhone("9876543210");
        u.setAddress("Street " + id);
        u.setCity("TestCity");
        u.setState("TestState");
        u.setLat(lat);
        u.setLng(lng);
        return u;
    }

    private Inventory createInventory(String bloodBankId, int aPos, int aNeg, int bPos, int bNeg, int oPos, int oNeg, int abPos, int abNeg) {
        Inventory inv = new Inventory();
        inv.setBloodBankId(bloodBankId);
        inv.setStockAPos(aPos);
        inv.setStockANeg(aNeg);
        inv.setStockBPos(bPos);
        inv.setStockBNeg(bNeg);
        inv.setStockOPos(oPos);
        inv.setStockONeg(oNeg);
        inv.setStockAbPos(abPos);
        inv.setStockAbNeg(abNeg);
        return inv;
    }

    private BloodRequest createRequest(String id, String hospitalId, String bloodGroup, int units, int radius) {
        BloodRequest br = new BloodRequest();
        br.setId(id);
        br.setHospitalId(hospitalId);
        br.setBloodGroup(bloodGroup);
        br.setUnits(units);
        br.setRadius(radius);
        br.setStatus("open");
        return br;
    }

    @Test
    public void testMatchExactAndCompatibleBloodGroups() {
        User hosp = createHospital("hosp1", 28.6139, 77.2090);
        when(userRepository.findById("hosp1")).thenReturn(Optional.of(hosp));

        BloodRequest req = createRequest("req1", "hosp1", "A+", 3, 20);
        when(bloodRequestRepository.findById("req1")).thenReturn(Optional.of(req));

        User bb1 = createBloodBank("bb1", "Bank 1", 28.62, 77.21); // Has A+ (5)
        User bb2 = createBloodBank("bb2", "Bank 2", 28.63, 77.21); // Has O+ (10) - compatible!
        User bb3 = createBloodBank("bb3", "Bank 3", 28.64, 77.21); // Has B+ (8) - incompatible!

        when(userRepository.findByRoleIgnoreCaseAndLatIsNotNullAndLngIsNotNull("bloodbank"))
                .thenReturn(Arrays.asList(bb1, bb2, bb3));

        when(inventoryRepository.findByBloodBankId("bb1"))
                .thenReturn(Optional.of(createInventory("bb1", 5, 0, 0, 0, 0, 0, 0, 0)));
        when(inventoryRepository.findByBloodBankId("bb2"))
                .thenReturn(Optional.of(createInventory("bb2", 0, 0, 0, 0, 10, 0, 0, 0)));
        when(inventoryRepository.findByBloodBankId("bb3"))
                .thenReturn(Optional.of(createInventory("bb3", 0, 0, 8, 0, 0, 0, 0, 0)));

        MatchBloodBanksResponseDTO result = bloodBankRequestService.matchBloodBanksForRequest("req1");

        assertNotNull(result);
        assertEquals(2, result.getTotalMatched(), "Should match bb1 (A+) and bb2 (O+), excluding bb3 (B+)");
        assertEquals(2, result.getBloodBanks().size());
        assertEquals("bb1", result.getBloodBanks().get(0).getId());
        assertEquals(5, result.getBloodBanks().get(0).getAvailableUnitsAtMatch());
        assertEquals(5, result.getBloodBanks().get(0).getCompatibleStock().get("A+"));

        assertEquals("bb2", result.getBloodBanks().get(1).getId());
        assertEquals(10, result.getBloodBanks().get(1).getAvailableUnitsAtMatch());
        assertEquals(10, result.getBloodBanks().get(1).getCompatibleStock().get("O+"));
    }

    @Test
    public void testMatchCompatibleGroupOnly() {
        User hosp = createHospital("hosp1", 28.6139, 77.2090);
        when(userRepository.findById("hosp1")).thenReturn(Optional.of(hosp));

        BloodRequest req = createRequest("req_compat_only", "hosp1", "A+", 5, 20);
        when(bloodRequestRepository.findById("req_compat_only")).thenReturn(Optional.of(req));

        User bb = createBloodBank("bb_o", "Bank O Only", 28.62, 77.21);
        when(userRepository.findByRoleIgnoreCaseAndLatIsNotNullAndLngIsNotNull("bloodbank"))
                .thenReturn(Collections.singletonList(bb));

        when(inventoryRepository.findByBloodBankId("bb_o"))
                .thenReturn(Optional.of(createInventory("bb_o", 0, 0, 0, 0, 5, 0, 0, 0))); // O+ = 5

        MatchBloodBanksResponseDTO result = bloodBankRequestService.matchBloodBanksForRequest("req_compat_only");

        assertNotNull(result);
        assertEquals(1, result.getTotalMatched());
        assertEquals("sufficient", result.getBloodBanks().get(0).getStatus());
        assertEquals(5, result.getBloodBanks().get(0).getAvailableUnitsAtMatch());
        assertEquals(5, result.getBloodBanks().get(0).getCompatibleStock().get("O+"));
    }

    @Test
    public void testMatchMultipleCompatibleGroups() {
        User hosp = createHospital("hosp1", 28.6139, 77.2090);
        when(userRepository.findById("hosp1")).thenReturn(Optional.of(hosp));

        BloodRequest req = createRequest("req_multi", "hosp1", "A+", 6, 20);
        when(bloodRequestRepository.findById("req_multi")).thenReturn(Optional.of(req));

        User bb = createBloodBank("bb_multi", "Bank Multi", 28.62, 77.21);
        when(userRepository.findByRoleIgnoreCaseAndLatIsNotNullAndLngIsNotNull("bloodbank"))
                .thenReturn(Collections.singletonList(bb));

        // A+ = 2, O+ = 3, A- = 1, B+ = 10 (incompatible)
        when(inventoryRepository.findByBloodBankId("bb_multi"))
                .thenReturn(Optional.of(createInventory("bb_multi", 2, 1, 10, 0, 3, 0, 0, 0)));

        MatchBloodBanksResponseDTO result = bloodBankRequestService.matchBloodBanksForRequest("req_multi");

        assertNotNull(result);
        assertEquals(1, result.getTotalMatched());
        assertEquals("sufficient", result.getBloodBanks().get(0).getStatus());
        assertEquals(6, result.getBloodBanks().get(0).getAvailableUnitsAtMatch()); // 2 + 3 + 1 = 6 (B+ ignored)
        assertEquals(2, result.getBloodBanks().get(0).getCompatibleStock().get("A+"));
        assertEquals(3, result.getBloodBanks().get(0).getCompatibleStock().get("O+"));
        assertEquals(1, result.getBloodBanks().get(0).getCompatibleStock().get("A-"));
        assertNull(result.getBloodBanks().get(0).getCompatibleStock().get("B+"));
    }

    @Test
    public void testMatchSufficientVsPartial() {
        User hosp = createHospital("hosp1", 28.6139, 77.2090);
        when(userRepository.findById("hosp1")).thenReturn(Optional.of(hosp));

        BloodRequest req = createRequest("req2", "hosp1", "B+", 5, 20);
        when(bloodRequestRepository.findById("req2")).thenReturn(Optional.of(req));

        User bb1 = createBloodBank("bb1", "Bank 1", 28.62, 77.21);
        User bb2 = createBloodBank("bb2", "Bank 2", 28.63, 77.21);

        when(userRepository.findByRoleIgnoreCaseAndLatIsNotNullAndLngIsNotNull("bloodbank"))
                .thenReturn(Arrays.asList(bb1, bb2));

        when(inventoryRepository.findByBloodBankId("bb1"))
                .thenReturn(Optional.of(createInventory("bb1", 0, 0, 5, 0, 0, 0, 0, 0))); // 5 >= 5 -> sufficient
        when(inventoryRepository.findByBloodBankId("bb2"))
                .thenReturn(Optional.of(createInventory("bb2", 0, 0, 2, 0, 0, 0, 0, 0))); // 2 < 5 -> partial

        MatchBloodBanksResponseDTO result = bloodBankRequestService.matchBloodBanksForRequest("req2");

        assertNotNull(result);
        assertEquals(2, result.getTotalMatched());
        assertEquals("sufficient", result.getBloodBanks().get(0).getStatus());
        assertEquals("partial", result.getBloodBanks().get(1).getStatus());
    }

    @Test
    public void testMatchRankingSufficientFirstThenDistance() {
        User hosp = createHospital("hosp1", 28.6139, 77.2090);
        when(userRepository.findById("hosp1")).thenReturn(Optional.of(hosp));

        BloodRequest req = createRequest("req3", "hosp1", "O+", 5, 30);
        when(bloodRequestRepository.findById("req3")).thenReturn(Optional.of(req));

        // bb1: Closer (~0.7km away), but partial stock (2 units)
        User bb1 = createBloodBank("bb1", "Bank Close Partial", 28.6200, 77.2090);
        // bb2: Farther (~6.7km away), but sufficient stock (10 units)
        User bb2 = createBloodBank("bb2", "Bank Far Sufficient", 28.6740, 77.2090);
        // bb3: Medium (~2.2km away), sufficient stock (5 units)
        User bb3 = createBloodBank("bb3", "Bank Mid Sufficient", 28.6340, 77.2090);

        when(userRepository.findByRoleIgnoreCaseAndLatIsNotNullAndLngIsNotNull("bloodbank"))
                .thenReturn(Arrays.asList(bb1, bb2, bb3));

        when(inventoryRepository.findByBloodBankId("bb1"))
                .thenReturn(Optional.of(createInventory("bb1", 0, 0, 0, 0, 2, 0, 0, 0)));
        when(inventoryRepository.findByBloodBankId("bb2"))
                .thenReturn(Optional.of(createInventory("bb2", 0, 0, 0, 0, 10, 0, 0, 0)));
        when(inventoryRepository.findByBloodBankId("bb3"))
                .thenReturn(Optional.of(createInventory("bb3", 0, 0, 0, 0, 5, 0, 0, 0)));

        MatchBloodBanksResponseDTO result = bloodBankRequestService.matchBloodBanksForRequest("req3");

        assertNotNull(result);
        assertEquals(3, result.getTotalMatched());
        // bb3 should be first (sufficient, ~2.2km), bb2 second (sufficient, ~6.7km), bb1 third (partial, ~0.7km)
        assertEquals("bb3", result.getBloodBanks().get(0).getId());
        assertEquals("sufficient", result.getBloodBanks().get(0).getStatus());

        assertEquals("bb2", result.getBloodBanks().get(1).getId());
        assertEquals("sufficient", result.getBloodBanks().get(1).getStatus());

        assertEquals("bb1", result.getBloodBanks().get(2).getId());
        assertEquals("partial", result.getBloodBanks().get(2).getStatus());
    }

    @Test
    public void testMatchStrictRadiusFilter() {
        User hosp = createHospital("hosp1", 28.6139, 77.2090);
        when(userRepository.findById("hosp1")).thenReturn(Optional.of(hosp));

        BloodRequest req = createRequest("req4", "hosp1", "AB+", 2, 10); // radius = 10km
        when(bloodRequestRepository.findById("req4")).thenReturn(Optional.of(req));

        User bbInside = createBloodBank("bb_in", "Inside Bank", 28.65, 77.2090); // ~4km
        User bbOutside = createBloodBank("bb_out", "Outside Bank", 28.76, 77.2090); // ~16km

        when(userRepository.findByRoleIgnoreCaseAndLatIsNotNullAndLngIsNotNull("bloodbank"))
                .thenReturn(Arrays.asList(bbInside, bbOutside));

        when(inventoryRepository.findByBloodBankId("bb_in"))
                .thenReturn(Optional.of(createInventory("bb_in", 0, 0, 0, 0, 0, 0, 5, 0)));
        when(inventoryRepository.findByBloodBankId("bb_out"))
                .thenReturn(Optional.of(createInventory("bb_out", 0, 0, 0, 0, 0, 0, 5, 0)));

        MatchBloodBanksResponseDTO result = bloodBankRequestService.matchBloodBanksForRequest("req4");

        assertNotNull(result);
        assertEquals(1, result.getTotalMatched());
        assertEquals("bb_in", result.getBloodBanks().get(0).getId());
    }

    @Test
    public void testNoRadiusExpansion() {
        User hosp = createHospital("hosp1", 28.6139, 77.2090);
        when(userRepository.findById("hosp1")).thenReturn(Optional.of(hosp));

        // Request radius = 5km
        BloodRequest req = createRequest("req5", "hosp1", "A-", 2, 5);
        when(bloodRequestRepository.findById("req5")).thenReturn(Optional.of(req));

        User bb1 = createBloodBank("bb1", "Bank 1 (3km)", 28.64, 77.2090); // ~2.9km
        User bb2 = createBloodBank("bb2", "Bank 2 (8km)", 28.685, 77.2090); // ~7.9km

        when(userRepository.findByRoleIgnoreCaseAndLatIsNotNullAndLngIsNotNull("bloodbank"))
                .thenReturn(Arrays.asList(bb1, bb2));

        when(inventoryRepository.findByBloodBankId("bb1"))
                .thenReturn(Optional.of(createInventory("bb1", 0, 4, 0, 0, 0, 0, 0, 0)));
        when(inventoryRepository.findByBloodBankId("bb2"))
                .thenReturn(Optional.of(createInventory("bb2", 0, 4, 0, 0, 0, 0, 0, 0)));

        MatchBloodBanksResponseDTO result = bloodBankRequestService.matchBloodBanksForRequest("req5");

        assertNotNull(result);
        assertEquals(1, result.getTotalMatched(), "Blood bank matching must NOT expand radius");
        assertEquals("bb1", result.getBloodBanks().get(0).getId());
    }

    @Test
    public void testMissingHospitalCoordinatesReturnsEmpty() {
        User hosp = createHospital("hosp_no_coords", null, null);
        when(userRepository.findById("hosp_no_coords")).thenReturn(Optional.of(hosp));

        BloodRequest req = createRequest("req6", "hosp_no_coords", "O-", 2, 15);
        when(bloodRequestRepository.findById("req6")).thenReturn(Optional.of(req));

        MatchBloodBanksResponseDTO result = bloodBankRequestService.matchBloodBanksForRequest("req6");

        assertNotNull(result);
        assertEquals(0, result.getTotalMatched());
        assertTrue(result.getBloodBanks().isEmpty());
    }

    @Test
    public void testParallelStatusesAllPending() {
        User hosp = createHospital("hosp1", 28.6139, 77.2090);
        when(userRepository.findById("hosp1")).thenReturn(Optional.of(hosp));

        BloodRequest req = createRequest("req7", "hosp1", "O+", 2, 20);
        when(bloodRequestRepository.findById("req7")).thenReturn(Optional.of(req));

        User bb1 = createBloodBank("bb1", "Bank 1", 28.62, 77.2090);
        User bb2 = createBloodBank("bb2", "Bank 2", 28.63, 77.2090);
        User bb3 = createBloodBank("bb3", "Bank 3", 28.64, 77.2090);

        when(userRepository.findByRoleIgnoreCaseAndLatIsNotNullAndLngIsNotNull("bloodbank"))
                .thenReturn(Arrays.asList(bb1, bb2, bb3));

        when(inventoryRepository.findByBloodBankId("bb1"))
                .thenReturn(Optional.of(createInventory("bb1", 0, 0, 0, 0, 5, 0, 0, 0)));
        when(inventoryRepository.findByBloodBankId("bb2"))
                .thenReturn(Optional.of(createInventory("bb2", 0, 0, 0, 0, 5, 0, 0, 0)));
        when(inventoryRepository.findByBloodBankId("bb3"))
                .thenReturn(Optional.of(createInventory("bb3", 0, 0, 0, 0, 5, 0, 0, 0)));

        MatchBloodBanksResponseDTO result = bloodBankRequestService.matchBloodBanksForRequest("req7");

        assertNotNull(result);
        assertEquals(3, result.getTotalMatched());

        MatchedBloodBankItemDTO first = result.getBloodBanks().get(0);
        assertEquals("pending", first.getResponseStatus(), "All matched blood banks must immediately be pending in parallel workflow");
        assertEquals(0, first.getUnitsSecured());
        assertNull(first.getRespondedAt());

        MatchedBloodBankItemDTO second = result.getBloodBanks().get(1);
        assertEquals("pending", second.getResponseStatus(), "All matched blood banks must immediately be pending in parallel workflow");
        assertEquals(0, second.getUnitsSecured());
        assertNull(second.getRespondedAt());

        MatchedBloodBankItemDTO third = result.getBloodBanks().get(2);
        assertEquals("pending", third.getResponseStatus(), "All matched blood banks must immediately be pending in parallel workflow");
        assertEquals(0, third.getUnitsSecured());
        assertNull(third.getRespondedAt());
    }

    @Test
    public void testZeroStockExclusion() {
        User hosp = createHospital("hosp1", 28.6139, 77.2090);
        when(userRepository.findById("hosp1")).thenReturn(Optional.of(hosp));

        BloodRequest req = createRequest("req8", "hosp1", "B-", 2, 20);
        when(bloodRequestRepository.findById("req8")).thenReturn(Optional.of(req));

        User bb1 = createBloodBank("bb1", "Bank Zero Stock", 28.62, 77.2090);
        User bb2 = createBloodBank("bb2", "Bank Null Inventory", 28.63, 77.2090);

        when(userRepository.findByRoleIgnoreCaseAndLatIsNotNullAndLngIsNotNull("bloodbank"))
                .thenReturn(Arrays.asList(bb1, bb2));

        when(inventoryRepository.findByBloodBankId("bb1"))
                .thenReturn(Optional.of(createInventory("bb1", 0, 0, 0, 0, 0, 0, 0, 0))); // 0 units
        when(inventoryRepository.findByBloodBankId("bb2"))
                .thenReturn(Optional.empty()); // null inventory

        MatchBloodBanksResponseDTO result = bloodBankRequestService.matchBloodBanksForRequest("req8");

        assertNotNull(result);
        assertEquals(0, result.getTotalMatched());
        assertTrue(result.getBloodBanks().isEmpty());
    }

    @Test
    public void testDistanceRoundingToOneDecimalPlace() {
        User hosp = createHospital("hosp1", 28.6139, 77.2090);
        when(userRepository.findById("hosp1")).thenReturn(Optional.of(hosp));

        BloodRequest req = createRequest("req9", "hosp1", "A+", 1, 20);
        when(bloodRequestRepository.findById("req9")).thenReturn(Optional.of(req));

        User bb = createBloodBank("bb1", "Bank 1", 28.62543, 77.21456);

        when(userRepository.findByRoleIgnoreCaseAndLatIsNotNullAndLngIsNotNull("bloodbank"))
                .thenReturn(Collections.singletonList(bb));

        when(inventoryRepository.findByBloodBankId("bb1"))
                .thenReturn(Optional.of(createInventory("bb1", 10, 0, 0, 0, 0, 0, 0, 0)));

        MatchBloodBanksResponseDTO result = bloodBankRequestService.matchBloodBanksForRequest("req9");

        assertNotNull(result);
        assertEquals(1, result.getTotalMatched());
        Double dist = result.getBloodBanks().get(0).getDistance();
        assertNotNull(dist);
        // Verify rounded to at most 1 decimal place (dist * 10 is an integer)
        assertEquals(Math.round(dist * 10.0), dist * 10.0, 0.0001);
    }

    @Test
    public void testRequestNotFoundThrowsException() {
        when(bloodRequestRepository.findById("non_existent")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> {
            bloodBankRequestService.matchBloodBanksForRequest("non_existent");
        });
    }
}