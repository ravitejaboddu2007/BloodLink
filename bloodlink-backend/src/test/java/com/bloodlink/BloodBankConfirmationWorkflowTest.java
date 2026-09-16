package com.bloodlink;

import com.bloodlink.controller.HospitalRequestController;
import com.bloodlink.dto.MatchBloodBanksResponseDTO;
import com.bloodlink.entity.BloodRequest;
import com.bloodlink.entity.Inventory;
import com.bloodlink.entity.RequestBloodBank;
import com.bloodlink.entity.User;
import com.bloodlink.repository.BloodRequestRepository;
import com.bloodlink.repository.InventoryRepository;
import com.bloodlink.repository.RequestBloodBankRepository;
import com.bloodlink.repository.RequestDonorRepository;
import com.bloodlink.repository.UserRepository;
import com.bloodlink.service.BloodBankRequestService;
import com.bloodlink.service.BloodRequestService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class BloodBankConfirmationWorkflowTest {

    private RequestBloodBankRepository requestBloodBankRepository;
    private BloodRequestRepository bloodRequestRepository;
    private RequestDonorRepository requestDonorRepository;
    private UserRepository userRepository;
    private InventoryRepository inventoryRepository;
    private ObjectMapper objectMapper;
    private BloodBankRequestService bloodBankRequestService;
    private BloodRequestService bloodRequestService;
    private HospitalRequestController hospitalRequestController;

    @BeforeEach
    public void setUp() {
        System.setProperty("net.bytebuddy.experimental", "true");
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

        bloodRequestService = Mockito.mock(BloodRequestService.class);
        hospitalRequestController = new HospitalRequestController(bloodRequestService, bloodBankRequestService);
    }

    private User createHospital(String id, double lat, double lng) {
        User u = new User();
        u.setId(id);
        u.setName("Apollo Hospital");
        u.setRole("hospital");
        u.setLat(lat);
        u.setLng(lng);
        return u;
    }

    private User createBloodBank(String id, double lat, double lng) {
        User u = new User();
        u.setId(id);
        u.setName("Red Cross Blood Bank");
        u.setRole("bloodbank");
        u.setLat(lat);
        u.setLng(lng);
        return u;
    }

    private BloodRequest createRequest(String id, String hospitalId, String bloodGroup, int units) {
        BloodRequest br = new BloodRequest();
        br.setId(id);
        br.setHospitalId(hospitalId);
        br.setBloodGroup(bloodGroup);
        br.setUnits(units);
        br.setSecuredUnits(0);
        br.setStatus("open");
        br.setRequestType("EMERGENCY");
        return br;
    }

    private RequestBloodBank createMatch(String bbId, BloodRequest req, int avail, String responseStatus) {
        RequestBloodBank rbb = new RequestBloodBank();
        rbb.setId(101L);
        rbb.setBloodBankId(bbId);
        rbb.setBloodRequest(req);
        rbb.setAvailableUnitsAtMatch(avail);
        rbb.setResponseStatus(responseStatus);
        rbb.setUnitsSecured(0);
        rbb.setReservedUnits(0);
        rbb.setUnavailable(false);
        return rbb;
    }

    private Inventory createInventory(String bbId, int aPosStock, int bPosStock) {
        Inventory inv = new Inventory();
        inv.setBloodBankId(bbId);
        inv.setStockAPos(aPosStock);
        inv.setStockBPos(bPosStock);
        return inv;
    }

    @Test
    public void test1_FullReservationOnBloodBankAccept() {
        // inv = 5, req = 3 -> inv becomes 2, reservedUnits = 3
        User bb = createBloodBank("bb1", 17.385, 78.486);
        when(userRepository.findById("bb1")).thenReturn(Optional.of(bb));

        BloodRequest req = createRequest("r1", "h1", "A+", 3);
        when(bloodRequestRepository.findById("r1")).thenReturn(Optional.of(req));

        Inventory inv = createInventory("bb1", 5, 10);
        when(inventoryRepository.findByBloodBankId("bb1")).thenReturn(Optional.of(inv));

        RequestBloodBank rbb = createMatch("bb1", req, 5, "pending");
        when(requestBloodBankRepository.findByBloodBankIdAndBloodRequestId("bb1", "r1"))
                .thenReturn(Optional.of(rbb));

        Map<String, Object> resp = bloodBankRequestService.respondToAlert("bb1", "r1", "accepted");

        assertNotNull(resp);
        assertEquals("accepted", resp.get("status"));
        assertEquals(3, resp.get("unitsSecured"));
        assertEquals(3, resp.get("reservedUnits"));
        assertEquals("accepted", rbb.getResponseStatus());
        assertEquals(3, rbb.getReservedUnits());
        assertEquals(3, rbb.getUnitsSecured());
        assertEquals(2, inv.getStockAPos(), "Inventory should be deducted from 5 to 2");
        verify(inventoryRepository, times(1)).save(inv);
        verify(requestBloodBankRepository, times(1)).save(rbb);
    }

    @Test
    public void test2_PartialReservationWhenInventoryLessThanRequested() {
        // inv = 2, req = 5 -> inv becomes 0, reservedUnits = 2
        User bb = createBloodBank("bb1", 17.385, 78.486);
        when(userRepository.findById("bb1")).thenReturn(Optional.of(bb));

        BloodRequest req = createRequest("r1", "h1", "A+", 5);
        when(bloodRequestRepository.findById("r1")).thenReturn(Optional.of(req));

        Inventory inv = createInventory("bb1", 2, 10);
        when(inventoryRepository.findByBloodBankId("bb1")).thenReturn(Optional.of(inv));

        RequestBloodBank rbb = createMatch("bb1", req, 2, "pending");
        when(requestBloodBankRepository.findByBloodBankIdAndBloodRequestId("bb1", "r1"))
                .thenReturn(Optional.of(rbb));

        Map<String, Object> resp = bloodBankRequestService.respondToAlert("bb1", "r1", "accepted");

        assertNotNull(resp);
        assertEquals("accepted", resp.get("status"));
        assertEquals(2, resp.get("unitsSecured"));
        assertEquals(2, resp.get("reservedUnits"));
        assertEquals(2, rbb.getReservedUnits());
        assertEquals(0, inv.getStockAPos(), "Inventory should be deducted from 2 to 0");
        verify(inventoryRepository, times(1)).save(inv);
    }

    @Test
    public void test3_CannotReserveBeyondInventoryNeverNegative() {
        // inv = 0, req = 3 -> inv remains 0, reservedUnits = 0
        User bb = createBloodBank("bb1", 17.385, 78.486);
        when(userRepository.findById("bb1")).thenReturn(Optional.of(bb));

        BloodRequest req = createRequest("r1", "h1", "A+", 3);
        when(bloodRequestRepository.findById("r1")).thenReturn(Optional.of(req));

        Inventory inv = createInventory("bb1", 0, 10);
        when(inventoryRepository.findByBloodBankId("bb1")).thenReturn(Optional.of(inv));

        RequestBloodBank rbb = createMatch("bb1", req, 0, "pending");
        when(requestBloodBankRepository.findByBloodBankIdAndBloodRequestId("bb1", "r1"))
                .thenReturn(Optional.of(rbb));

        Map<String, Object> resp = bloodBankRequestService.respondToAlert("bb1", "r1", "accepted");

        assertNotNull(resp);
        assertEquals(0, resp.get("unitsSecured"));
        assertEquals(0, resp.get("reservedUnits"));
        assertEquals(0, inv.getStockAPos());
    }

    @Test
    public void test4_HospitalConfirmsAcceptedBloodBankInventoryUnchangedReservedUnitsZeroed() {
        // After accept: inv=2, reservedUnits=3
        // After confirm: inv remains 2 (not deducted again), reservedUnits=0, unitsSecured=3, request fulfilled
        User bb = createBloodBank("bb1", 17.385, 78.486);
        when(userRepository.findById("bb1")).thenReturn(Optional.of(bb));

        BloodRequest req = createRequest("r1", "h1", "A+", 3);
        when(bloodRequestRepository.findById("r1")).thenReturn(Optional.of(req));
        when(bloodRequestRepository.save(any(BloodRequest.class))).thenAnswer(i -> i.getArgument(0));

        Inventory inv = createInventory("bb1", 2, 10);
        when(inventoryRepository.findByBloodBankId("bb1")).thenReturn(Optional.of(inv));

        RequestBloodBank rbb = createMatch("bb1", req, 5, "accepted");
        rbb.setReservedUnits(3);
        rbb.setUnitsSecured(3);
        when(requestBloodBankRepository.findByBloodBankIdAndBloodRequestId("bb1", "r1"))
                .thenReturn(Optional.of(rbb));
        when(requestBloodBankRepository.findByBloodRequestId("r1"))
                .thenReturn(Collections.singletonList(rbb));
        when(requestDonorRepository.findByBloodRequestId("r1"))
                .thenReturn(Collections.emptyList());

        BloodRequest result = bloodBankRequestService.confirmBloodBank("r1", "bb1", "h1");

        assertNotNull(result);
        assertEquals("confirmed", rbb.getResponseStatus());
        assertEquals(0, rbb.getReservedUnits(), "Reserved units must reset to 0 upon confirmation");
        assertEquals(3, rbb.getUnitsSecured());
        assertEquals(3, result.getSecuredUnits());
        assertEquals("fulfilled", result.getStatus());
        assertEquals(2, inv.getStockAPos(), "Inventory must remain 2 (not deducted again)");
        verify(inventoryRepository, never()).save(any());
    }

    @Test
    public void test5_HospitalRejectsAcceptedBloodBankReservedUnitsRestoredToInventory() {
        // After accept: inv=2, reservedUnits=3
        // After reject: inv restored to 2+3=5, reservedUnits=0, unitsSecured=0
        User bb = createBloodBank("bb1", 17.385, 78.486);
        when(userRepository.findById("bb1")).thenReturn(Optional.of(bb));

        BloodRequest req = createRequest("r1", "h1", "A+", 3);
        when(bloodRequestRepository.findById("r1")).thenReturn(Optional.of(req));
        when(bloodRequestRepository.save(any(BloodRequest.class))).thenAnswer(i -> i.getArgument(0));

        Inventory inv = createInventory("bb1", 2, 10);
        when(inventoryRepository.findByBloodBankId("bb1")).thenReturn(Optional.of(inv));

        RequestBloodBank rbb = createMatch("bb1", req, 5, "accepted");
        rbb.setReservedUnits(3);
        rbb.setUnitsSecured(3);
        when(requestBloodBankRepository.findByBloodBankIdAndBloodRequestId("bb1", "r1"))
                .thenReturn(Optional.of(rbb));
        when(requestBloodBankRepository.findByBloodRequestId("r1"))
                .thenReturn(Collections.singletonList(rbb));
        when(requestDonorRepository.findByBloodRequestId("r1"))
                .thenReturn(Collections.emptyList());

        BloodRequest result = bloodBankRequestService.rejectBloodBank("r1", "bb1", "h1");

        assertNotNull(result);
        assertEquals("rejected", rbb.getResponseStatus());
        assertEquals(0, rbb.getReservedUnits(), "Reserved units must reset to 0 upon reject");
        assertEquals(0, rbb.getUnitsSecured());
        assertTrue(rbb.getUnavailable());
        assertEquals(5, inv.getStockAPos(), "Inventory must be restored from 2 back to 5");
        verify(inventoryRepository, times(1)).save(inv);
    }

    @Test
    public void test6_MatchingIgnoresReservedUnitsUsesCurrentInventoryStock() {
        User hospital = createHospital("h1", 17.385044, 78.486671);
        when(userRepository.findById("h1")).thenReturn(Optional.of(hospital));

        BloodRequest req = createRequest("r1", "h1", "A+", 3);
        req.setRadius(10);
        when(bloodRequestRepository.findById("r1")).thenReturn(Optional.of(req));

        User bb = createBloodBank("bb1", 17.386000, 78.487000);
        when(userRepository.findByRoleIgnoreCaseAndLatIsNotNullAndLngIsNotNull("bloodbank"))
                .thenReturn(Collections.singletonList(bb));

        // Available inventory stock after reservation is 2
        Inventory inv = createInventory("bb1", 2, 0);
        when(inventoryRepository.findByBloodBankId("bb1")).thenReturn(Optional.of(inv));

        MatchBloodBanksResponseDTO matchResult = bloodBankRequestService.matchBloodBanksForRequest("r1");

        assertNotNull(matchResult);
        assertEquals(1, matchResult.getBloodBanks().size());
        assertEquals(2, matchResult.getBloodBanks().get(0).getAvailableUnitsAtMatch(), "Match should see only available inventory (2)");
        assertEquals("partial", matchResult.getBloodBanks().get(0).getStatus());
    }

    @Test
    public void test7_MultipleRequestsCannotOversell() {
        User bb = createBloodBank("bb1", 17.385, 78.486);
        when(userRepository.findById("bb1")).thenReturn(Optional.of(bb));

        // Initial inventory has 4 units of A+
        Inventory inv = createInventory("bb1", 4, 10);
        when(inventoryRepository.findByBloodBankId("bb1")).thenReturn(Optional.of(inv));

        // Request 1 asks for 3 units
        BloodRequest req1 = createRequest("r1", "h1", "A+", 3);
        when(bloodRequestRepository.findById("r1")).thenReturn(Optional.of(req1));
        RequestBloodBank rbb1 = createMatch("bb1", req1, 4, "pending");
        when(requestBloodBankRepository.findByBloodBankIdAndBloodRequestId("bb1", "r1"))
                .thenReturn(Optional.of(rbb1));

        Map<String, Object> resp1 = bloodBankRequestService.respondToAlert("bb1", "r1", "accepted");
        assertEquals(3, resp1.get("reservedUnits"));
        assertEquals(1, inv.getStockAPos(), "Inventory reduced to 1");

        // Request 2 asks for 3 units, but only 1 is left in available inventory
        BloodRequest req2 = createRequest("r2", "h1", "A+", 3);
        when(bloodRequestRepository.findById("r2")).thenReturn(Optional.of(req2));
        RequestBloodBank rbb2 = createMatch("bb1", req2, 4, "pending");
        when(requestBloodBankRepository.findByBloodBankIdAndBloodRequestId("bb1", "r2"))
                .thenReturn(Optional.of(rbb2));

        Map<String, Object> resp2 = bloodBankRequestService.respondToAlert("bb1", "r2", "accepted");
        assertEquals(1, resp2.get("reservedUnits"), "Only 1 unit can be reserved for req2");
        assertEquals(0, inv.getStockAPos(), "Inventory reduced to 0");
    }

    @Test
    public void test8_BloodGroupIsolation() {
        // Deducting A+ inventory should not affect B+ inventory
        User bb = createBloodBank("bb1", 17.385, 78.486);
        when(userRepository.findById("bb1")).thenReturn(Optional.of(bb));

        BloodRequest req = createRequest("r1", "h1", "A+", 3);
        when(bloodRequestRepository.findById("r1")).thenReturn(Optional.of(req));

        Inventory inv = createInventory("bb1", 5, 10);
        when(inventoryRepository.findByBloodBankId("bb1")).thenReturn(Optional.of(inv));

        RequestBloodBank rbb = createMatch("bb1", req, 5, "pending");
        when(requestBloodBankRepository.findByBloodBankIdAndBloodRequestId("bb1", "r1"))
                .thenReturn(Optional.of(rbb));

        bloodBankRequestService.respondToAlert("bb1", "r1", "accepted");

        assertEquals(2, inv.getStockAPos(), "A+ stock reduced to 2");
        assertEquals(10, inv.getStockBPos(), "B+ stock must remain untouched at 10");
    }

    @Test
    public void test9_BloodBankRejectsAlertDirectlyNoInventoryDeducted() {
        User bb = createBloodBank("bb1", 17.385, 78.486);
        when(userRepository.findById("bb1")).thenReturn(Optional.of(bb));

        BloodRequest req = createRequest("r1", "h1", "A+", 3);
        when(bloodRequestRepository.findById("r1")).thenReturn(Optional.of(req));

        Inventory inv = createInventory("bb1", 5, 10);
        when(inventoryRepository.findByBloodBankId("bb1")).thenReturn(Optional.of(inv));

        RequestBloodBank rbb = createMatch("bb1", req, 5, "pending");
        when(requestBloodBankRepository.findByBloodBankIdAndBloodRequestId("bb1", "r1"))
                .thenReturn(Optional.of(rbb));

        Map<String, Object> resp = bloodBankRequestService.respondToAlert("bb1", "r1", "rejected");

        assertEquals("rejected", resp.get("status"));
        assertEquals(0, rbb.getReservedUnits());
        assertEquals(0, rbb.getUnitsSecured());
        assertEquals(5, inv.getStockAPos(), "Inventory must remain 5");
        verify(inventoryRepository, never()).save(any());
    }

    @Test
    public void test10_HospitalCannotConfirmBeforeBloodBankAccepts() {
        User bb = createBloodBank("bb1", 17.385, 78.486);
        when(userRepository.findById("bb1")).thenReturn(Optional.of(bb));

        BloodRequest req = createRequest("r1", "h1", "A+", 2);
        when(bloodRequestRepository.findById("r1")).thenReturn(Optional.of(req));

        RequestBloodBank rbb = createMatch("bb1", req, 5, "pending");
        when(requestBloodBankRepository.findByBloodBankIdAndBloodRequestId("bb1", "r1"))
                .thenReturn(Optional.of(rbb));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            bloodBankRequestService.confirmBloodBank("r1", "bb1", "h1");
        });
        assertEquals("BLOOD_BANK_NOT_ACCEPTED", ex.getMessage());
    }

    @Test
    public void test11_HospitalCannotRejectBeforeBloodBankAccepts() {
        User bb = createBloodBank("bb1", 17.385, 78.486);
        when(userRepository.findById("bb1")).thenReturn(Optional.of(bb));

        BloodRequest req = createRequest("r1", "h1", "A+", 2);
        when(bloodRequestRepository.findById("r1")).thenReturn(Optional.of(req));

        RequestBloodBank rbb = createMatch("bb1", req, 5, "waiting");
        when(requestBloodBankRepository.findByBloodBankIdAndBloodRequestId("bb1", "r1"))
                .thenReturn(Optional.of(rbb));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            bloodBankRequestService.rejectBloodBank("r1", "bb1", "h1");
        });
        assertEquals("BLOOD_BANK_NOT_ACCEPTED", ex.getMessage());
    }

    @Test
    public void test12_StateTransitionsIdempotentAndRestricted() {
        User bb = createBloodBank("bb1", 17.385, 78.486);
        when(userRepository.findById("bb1")).thenReturn(Optional.of(bb));

        BloodRequest req = createRequest("r1", "h1", "A+", 2);
        when(bloodRequestRepository.findById("r1")).thenReturn(Optional.of(req));

        RequestBloodBank rbb = createMatch("bb1", req, 5, "confirmed");
        when(requestBloodBankRepository.findByBloodBankIdAndBloodRequestId("bb1", "r1"))
                .thenReturn(Optional.of(rbb));

        // Confirmed cannot be rejected
        IllegalArgumentException ex1 = assertThrows(IllegalArgumentException.class, () -> {
            bloodBankRequestService.rejectBloodBank("r1", "bb1", "h1");
        });
        assertEquals("ALREADY_CONFIRMED", ex1.getMessage());

        // Rejected cannot be confirmed
        RequestBloodBank rbbRejected = createMatch("bb1", req, 5, "rejected");
        when(requestBloodBankRepository.findByBloodBankIdAndBloodRequestId("bb1", "r1"))
                .thenReturn(Optional.of(rbbRejected));
        IllegalArgumentException ex2 = assertThrows(IllegalArgumentException.class, () -> {
            bloodBankRequestService.confirmBloodBank("r1", "bb1", "h1");
        });
        assertEquals("ALREADY_REJECTED", ex2.getMessage());
    }

    @Test
    public void test13_HospitalControllerConfirmAndRejectEndpoints() {
        User bb = createBloodBank("bb1", 17.385, 78.486);
        when(userRepository.findById("bb1")).thenReturn(Optional.of(bb));

        BloodRequest req = createRequest("r1", "h1", "A+", 2);
        when(bloodRequestRepository.findById("r1")).thenReturn(Optional.of(req));
        when(bloodRequestRepository.save(any(BloodRequest.class))).thenAnswer(i -> i.getArgument(0));

        RequestBloodBank rbb = createMatch("bb1", req, 5, "accepted");
        when(requestBloodBankRepository.findByBloodBankIdAndBloodRequestId("bb1", "r1"))
                .thenReturn(Optional.of(rbb));
        when(requestBloodBankRepository.findByBloodRequestId("r1"))
                .thenReturn(Collections.singletonList(rbb));
        when(requestDonorRepository.findByBloodRequestId("r1"))
                .thenReturn(Collections.emptyList());

        // Test PUT confirm
        ResponseEntity<?> confirmResp = hospitalRequestController.confirmBloodBank("r1", "bb1", Collections.singletonMap("hospitalId", "h1"));
        assertEquals(HttpStatus.OK, confirmResp.getStatusCode());
        assertTrue(confirmResp.getBody() instanceof BloodRequest);

        // Test Unauthorized
        ResponseEntity<?> unauthResp = hospitalRequestController.confirmBloodBank("r1", "bb1", Collections.singletonMap("hospitalId", "wrong_hosp"));
        assertEquals(HttpStatus.FORBIDDEN, unauthResp.getStatusCode());

        // Test PUT reject for pending bank
        RequestBloodBank rbbPending = createMatch("bb1", req, 5, "pending");
        when(requestBloodBankRepository.findByBloodBankIdAndBloodRequestId("bb1", "r1"))
                .thenReturn(Optional.of(rbbPending));
        ResponseEntity<?> rejectNotAccepted = hospitalRequestController.rejectBloodBank("r1", "bb1", Collections.singletonMap("hospitalId", "h1"));
        assertEquals(HttpStatus.BAD_REQUEST, rejectNotAccepted.getStatusCode());
    }

    @Test
    public void test14_MixedGroupReservationAndExactRestoration() {
        // Request: A+ 5 units. Inventory: A+ = 2, O+ = 3.
        User bb = createBloodBank("bb1", 17.385, 78.486);
        when(userRepository.findById("bb1")).thenReturn(Optional.of(bb));

        BloodRequest req = createRequest("r_mixed", "h1", "A+", 5);
        when(bloodRequestRepository.findById("r_mixed")).thenReturn(Optional.of(req));

        Inventory inv = new Inventory();
        inv.setBloodBankId("bb1");
        inv.setStockAPos(2);
        inv.setStockOPos(3);
        when(inventoryRepository.findByBloodBankId("bb1")).thenReturn(Optional.of(inv));

        RequestBloodBank rbb = createMatch("bb1", req, 5, "pending");
        when(requestBloodBankRepository.findByBloodBankIdAndBloodRequestId("bb1", "r_mixed"))
                .thenReturn(Optional.of(rbb));

        // 1. Accept
        Map<String, Object> resp = bloodBankRequestService.respondToAlert("bb1", "r_mixed", "accepted");
        assertNotNull(resp);
        assertEquals("accepted", resp.get("status"));
        assertEquals(5, resp.get("reservedUnits"));
        assertEquals(0, inv.getStockAPos(), "A+ stock should be consumed from 2 to 0");
        assertEquals(0, inv.getStockOPos(), "O+ stock should be consumed from 3 to 0");
        assertNotNull(rbb.getReservedGroupsJson());
        assertTrue(rbb.getReservedGroupsJson().contains("\"A+\":2") && rbb.getReservedGroupsJson().contains("\"O+\":3"));

        // 2. Reject -> should restore exactly A+=2, O+=3
        bloodBankRequestService.rejectBloodBank("r_mixed", "bb1", "h1");
        assertEquals(2, inv.getStockAPos(), "A+ stock should be restored to 2");
        assertEquals(3, inv.getStockOPos(), "O+ stock should be restored to 3");
        assertEquals(0, rbb.getReservedUnits());
        assertNull(rbb.getReservedGroupsJson());
    }

    @Test
    public void test15_CompatibleOnlyReservationAndRestoration() {
        // Request: A+ 3 units. Inventory: A+ = 0, O+ = 5.
        User bb = createBloodBank("bb1", 17.385, 78.486);
        when(userRepository.findById("bb1")).thenReturn(Optional.of(bb));

        BloodRequest req = createRequest("r_compat", "h1", "A+", 3);
        when(bloodRequestRepository.findById("r_compat")).thenReturn(Optional.of(req));

        Inventory inv = new Inventory();
        inv.setBloodBankId("bb1");
        inv.setStockAPos(0);
        inv.setStockOPos(5);
        when(inventoryRepository.findByBloodBankId("bb1")).thenReturn(Optional.of(inv));

        RequestBloodBank rbb = createMatch("bb1", req, 5, "pending");
        when(requestBloodBankRepository.findByBloodBankIdAndBloodRequestId("bb1", "r_compat"))
                .thenReturn(Optional.of(rbb));

        // 1. Accept
        Map<String, Object> resp = bloodBankRequestService.respondToAlert("bb1", "r_compat", "accepted");
        assertEquals("accepted", resp.get("status"));
        assertEquals(3, resp.get("reservedUnits"));
        assertEquals(0, inv.getStockAPos(), "A+ remains 0");
        assertEquals(2, inv.getStockOPos(), "O+ consumed from 5 to 2");

        // 2. Reject -> restores O+ back to 5
        bloodBankRequestService.respondToAlert("bb1", "r_compat", "rejected");
        assertEquals(0, inv.getStockAPos(), "A+ remains 0");
        assertEquals(5, inv.getStockOPos(), "O+ restored from 2 to 5");
    }

    @Test
    public void test16_HospitalConfirmDoesNotDeductInventoryAgain() {
        // Request: A+ 4 units. Inventory: A+ = 2, O+ = 2.
        User bb = createBloodBank("bb1", 17.385, 78.486);
        when(userRepository.findById("bb1")).thenReturn(Optional.of(bb));

        BloodRequest req = createRequest("r_conf", "h1", "A+", 4);
        when(bloodRequestRepository.findById("r_conf")).thenReturn(Optional.of(req));
        when(bloodRequestRepository.save(any(BloodRequest.class))).thenAnswer(i -> i.getArgument(0));

        Inventory inv = new Inventory();
        inv.setBloodBankId("bb1");
        inv.setStockAPos(2);
        inv.setStockOPos(2);
        when(inventoryRepository.findByBloodBankId("bb1")).thenReturn(Optional.of(inv));

        RequestBloodBank rbb = createMatch("bb1", req, 4, "pending");
        when(requestBloodBankRepository.findByBloodBankIdAndBloodRequestId("bb1", "r_conf"))
                .thenReturn(Optional.of(rbb));
        when(requestBloodBankRepository.findByBloodRequestId("r_conf"))
                .thenReturn(Collections.singletonList(rbb));
        when(requestDonorRepository.findByBloodRequestId("r_conf"))
                .thenReturn(Collections.emptyList());

        // 1. Accept
        bloodBankRequestService.respondToAlert("bb1", "r_conf", "accepted");
        assertEquals(0, inv.getStockAPos());
        assertEquals(0, inv.getStockOPos());

        // 2. Confirm
        BloodRequest updated = bloodBankRequestService.confirmBloodBank("r_conf", "bb1", "h1");
        assertNotNull(updated);
        assertEquals("fulfilled", updated.getStatus());
        assertEquals(4, updated.getSecuredUnits());
        assertEquals(0, inv.getStockAPos(), "A+ must remain 0 - no double deduction");
        assertEquals(0, inv.getStockOPos(), "O+ must remain 0 - no double deduction");
    }
}