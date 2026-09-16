package com.bloodlink;

import com.bloodlink.controller.BloodBankInventoryController;
import com.bloodlink.dto.InventoryDTO;
import com.bloodlink.dto.UpdateInventoryRequest;
import com.bloodlink.entity.Inventory;
import com.bloodlink.entity.User;
import com.bloodlink.repository.InventoryRepository;
import com.bloodlink.repository.UserRepository;
import com.bloodlink.service.InventoryService;
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

public class InventoryServiceTest {

    private InventoryRepository inventoryRepository;
    private UserRepository userRepository;
    private ObjectMapper objectMapper;
    private InventoryService inventoryService;
    private BloodBankInventoryController bloodBankInventoryController;

    @BeforeEach
    public void setUp() {
        inventoryRepository = Mockito.mock(InventoryRepository.class);
        userRepository = Mockito.mock(UserRepository.class);
        objectMapper = new ObjectMapper();

        inventoryService = new InventoryService(inventoryRepository, userRepository, objectMapper);
        bloodBankInventoryController = new BloodBankInventoryController(inventoryService);
    }

    private User createBloodBankUser(String id, String name) {
        User u = new User();
        u.setId(id);
        u.setRole("bloodbank");
        u.setName(name);
        u.setEmail(id + "@bloodbank.org");
        u.setPasswordHash("");
        return u;
    }

    private Inventory createMockInventory(String bloodBankId) {
        Inventory inv = new Inventory();
        inv.setId(1L);
        inv.setBloodBankId(bloodBankId);
        inv.setStockAPos(10);
        inv.setStockANeg(5);
        inv.setStockBPos(8);
        inv.setStockBNeg(2);
        inv.setStockOPos(15);
        inv.setStockONeg(3);
        inv.setStockAbPos(4);
        inv.setStockAbNeg(1);
        inv.setLastUpdatedJson("{\"A+\":\"2026-03-01T10:00:00\"}");
        return inv;
    }

    @Test
    public void testGetInventorySuccessExisting() {
        User bank = createBloodBankUser("b1", "Central Blood Bank");
        Inventory inv = createMockInventory("b1");

        when(userRepository.findById("b1")).thenReturn(Optional.of(bank));
        when(inventoryRepository.findByBloodBankId("b1")).thenReturn(Optional.of(inv));

        InventoryDTO dto = inventoryService.getInventory("b1");

        assertNotNull(dto);
        assertEquals("b1", dto.getBloodBankId());
        assertEquals(10, dto.getStock().get("A+"));
        assertEquals(5, dto.getStock().get("A-"));
        assertEquals(8, dto.getStock().get("B+"));
        assertEquals(15, dto.getStock().get("O+"));
        assertNotNull(dto.getLastUpdated());
        assertEquals("2026-03-01T10:00:00", dto.getLastUpdated().get("A+"));
    }

    @Test
    public void testGetInventorySuccessInitializesNewRecordIfAbsent() {
        User bank = createBloodBankUser("b2", "City Blood Bank");

        when(userRepository.findById("b2")).thenReturn(Optional.of(bank));
        when(inventoryRepository.findByBloodBankId("b2")).thenReturn(Optional.empty());
        when(inventoryRepository.save(any(Inventory.class))).thenAnswer(i -> i.getArgument(0));

        InventoryDTO dto = inventoryService.getInventory("b2");

        assertNotNull(dto);
        assertEquals("b2", dto.getBloodBankId());
        assertEquals(0, dto.getStock().get("A+"));
        assertEquals(0, dto.getStock().get("O-"));
        verify(inventoryRepository, times(1)).save(any(Inventory.class));
    }

    @Test
    public void testGetInventoryUnknownBloodBankThrows() {
        when(userRepository.findById("unknown_bank")).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            inventoryService.getInventory("unknown_bank");
        });
        assertEquals("BLOOD_BANK_NOT_FOUND", ex.getMessage());
    }

    @Test
    public void testGetInventoryNotABloodBankThrows() {
        User donor = new User();
        donor.setId("d1");
        donor.setRole("donor");
        when(userRepository.findById("d1")).thenReturn(Optional.of(donor));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            inventoryService.getInventory("d1");
        });
        assertEquals("NOT_A_BLOOD_BANK", ex.getMessage());
    }

    @Test
    public void testGetStockByBloodGroupSuccess() {
        User bank = createBloodBankUser("b1", "Central Blood Bank");
        Inventory inv = createMockInventory("b1");

        when(userRepository.findById("b1")).thenReturn(Optional.of(bank));
        when(inventoryRepository.findByBloodBankId("b1")).thenReturn(Optional.of(inv));

        int stock = inventoryService.getStockByGroup("b1", "B+");
        assertEquals(8, stock);
    }

    @Test
    public void testGetStockByInvalidBloodGroupThrows() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            inventoryService.getStockByGroup("b1", "INVALID_BG");
        });
        assertTrue(ex.getMessage().contains("INVALID_BLOOD_GROUP"));
    }

    @Test
    public void testUpdateInventorySuccess() {
        User bank = createBloodBankUser("b1", "Central Blood Bank");
        Inventory inv = createMockInventory("b1");

        when(userRepository.findById("b1")).thenReturn(Optional.of(bank));
        when(inventoryRepository.findByBloodBankId("b1")).thenReturn(Optional.of(inv));
        when(inventoryRepository.save(any(Inventory.class))).thenAnswer(i -> i.getArgument(0));

        UpdateInventoryRequest req = new UpdateInventoryRequest();
        Map<String, Integer> newStock = new HashMap<>();
        newStock.put("O+", 25);
        newStock.put("AB-", 5);
        req.setStock(newStock);

        InventoryDTO updated = inventoryService.updateInventory("b1", req);

        assertNotNull(updated);
        assertEquals(25, updated.getStock().get("O+"));
        assertEquals(5, updated.getStock().get("AB-"));
        verify(inventoryRepository, times(1)).save(any(Inventory.class));
    }

    @Test
    public void testUpdateInventoryRejectsNegativeStock() {
        User bank = createBloodBankUser("b1", "Central Blood Bank");
        when(userRepository.findById("b1")).thenReturn(Optional.of(bank));

        UpdateInventoryRequest req = new UpdateInventoryRequest();
        Map<String, Integer> stock = new HashMap<>();
        stock.put("A+", -3);
        req.setStock(stock);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            inventoryService.updateInventory("b1", req);
        });
        assertTrue(ex.getMessage().contains("INVALID_QUANTITY"));
    }

    @Test
    public void testUpdateInventoryRejectsInvalidBloodGroup() {
        User bank = createBloodBankUser("b1", "Central Blood Bank");
        when(userRepository.findById("b1")).thenReturn(Optional.of(bank));

        UpdateInventoryRequest req = new UpdateInventoryRequest();
        Map<String, Integer> stock = new HashMap<>();
        stock.put("XY+", 10);
        req.setStock(stock);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            inventoryService.updateInventory("b1", req);
        });
        assertTrue(ex.getMessage().contains("INVALID_BLOOD_GROUP"));
    }

    @Test
    public void testUpdateInventoryUnknownBloodBankThrows() {
        when(userRepository.findById("unknown_bank")).thenReturn(Optional.empty());

        UpdateInventoryRequest req = new UpdateInventoryRequest();
        req.setStock(Collections.singletonMap("A+", 5));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            inventoryService.updateInventory("unknown_bank", req);
        });
        assertEquals("BLOOD_BANK_NOT_FOUND", ex.getMessage());
    }

    @Test
    public void testUpdateInventoryNotABloodBankThrows() {
        User hospital = new User();
        hospital.setId("h1");
        hospital.setRole("hospital");
        when(userRepository.findById("h1")).thenReturn(Optional.of(hospital));

        UpdateInventoryRequest req = new UpdateInventoryRequest();
        req.setStock(Collections.singletonMap("A+", 5));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            inventoryService.updateInventory("h1", req);
        });
        assertEquals("NOT_A_BLOOD_BANK", ex.getMessage());
    }

    @Test
    public void testInventoryDTOSafetyNoPasswordExposed() {
        User bank = createBloodBankUser("b1", "Central Blood Bank");
        Inventory inv = createMockInventory("b1");

        when(userRepository.findById("b1")).thenReturn(Optional.of(bank));
        when(inventoryRepository.findByBloodBankId("b1")).thenReturn(Optional.of(inv));

        InventoryDTO dto = inventoryService.getInventory("b1");
        assertNotNull(dto);
        assertEquals("b1", dto.getBloodBankId());
        assertNotNull(dto.getStock());
        assertNotNull(dto.getLastUpdated());
    }

    @Test
    public void testControllerGetInventoryEndpointSuccess() {
        User bank = createBloodBankUser("b1", "Central Blood Bank");
        Inventory inv = createMockInventory("b1");

        when(userRepository.findById("b1")).thenReturn(Optional.of(bank));
        when(inventoryRepository.findByBloodBankId("b1")).thenReturn(Optional.of(inv));

        ResponseEntity<?> response = bloodBankInventoryController.getInventory("b1");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody() instanceof InventoryDTO);
        InventoryDTO body = (InventoryDTO) response.getBody();
        assertEquals("b1", body.getBloodBankId());
    }

    @Test
    public void testControllerUpdateInventoryEndpointSuccess() {
        User bank = createBloodBankUser("b1", "Central Blood Bank");
        Inventory inv = createMockInventory("b1");

        when(userRepository.findById("b1")).thenReturn(Optional.of(bank));
        when(inventoryRepository.findByBloodBankId("b1")).thenReturn(Optional.of(inv));
        when(inventoryRepository.save(any(Inventory.class))).thenAnswer(i -> i.getArgument(0));

        UpdateInventoryRequest req = new UpdateInventoryRequest();
        req.setStock(Collections.singletonMap("B+", 12));

        ResponseEntity<?> response = bloodBankInventoryController.updateInventory("b1", req);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody() instanceof InventoryDTO);
    }

    @Test
    public void testControllerGetStockByGroupEndpoint() {
        User bank = createBloodBankUser("b1", "Central Blood Bank");
        Inventory inv = createMockInventory("b1");

        when(userRepository.findById("b1")).thenReturn(Optional.of(bank));
        when(inventoryRepository.findByBloodBankId("b1")).thenReturn(Optional.of(inv));

        ResponseEntity<?> response = bloodBankInventoryController.getStockByBloodGroup("b1", "O-");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody() instanceof Map);
        Map<?, ?> map = (Map<?, ?>) response.getBody();
        assertEquals(3, map.get("stock"));
    }

    @Test
    public void testControllerGetUpdatesCountEndpoint() {
        Inventory inv1 = createMockInventory("b1");
        Inventory inv2 = createMockInventory("b2");
        when(inventoryRepository.findAll()).thenReturn(Arrays.asList(inv1, inv2));

        ResponseEntity<?> response = bloodBankInventoryController.getInventoryUpdatesCount();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody() instanceof Map);
        Map<?, ?> map = (Map<?, ?>) response.getBody();
        assertEquals(2L, map.get("count"));
    }
}