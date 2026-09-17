package com.bloodlink;

import com.bloodlink.controller.HospitalRequestController;
import com.bloodlink.dto.CreateBloodRequestRequest;
import com.bloodlink.dto.HospitalRequestDTO;
import com.bloodlink.entity.BloodRequest;
import com.bloodlink.entity.RequestBloodBank;
import com.bloodlink.entity.RequestDonor;
import com.bloodlink.entity.User;
import com.bloodlink.repository.BloodRequestRepository;
import com.bloodlink.repository.DonationHistoryRepository;
import com.bloodlink.repository.RequestBloodBankRepository;
import com.bloodlink.repository.RequestDonorRepository;
import com.bloodlink.repository.UserRepository;
import com.bloodlink.service.BloodBankRequestService;
import com.bloodlink.service.BloodRequestService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

public class BloodRequestServiceTest {

    private BloodRequestRepository bloodRequestRepository;
    private RequestDonorRepository requestDonorRepository;
    private RequestBloodBankRepository requestBloodBankRepository;
    private DonationHistoryRepository donationHistoryRepository;
    private UserRepository userRepository;
    private BloodBankRequestService bloodBankRequestService;

    private BloodRequestService bloodRequestService;
    private HospitalRequestController hospitalRequestController;

    @BeforeEach
    public void setUp() {
        bloodRequestRepository = Mockito.mock(BloodRequestRepository.class);
        requestDonorRepository = Mockito.mock(RequestDonorRepository.class);
        requestBloodBankRepository = Mockito.mock(RequestBloodBankRepository.class);
        donationHistoryRepository = Mockito.mock(DonationHistoryRepository.class);
        userRepository = Mockito.mock(UserRepository.class);
        bloodBankRequestService = Mockito.mock(BloodBankRequestService.class);

        bloodRequestService = new BloodRequestService(
                bloodRequestRepository,
                requestDonorRepository,
                requestBloodBankRepository,
                donationHistoryRepository,
                userRepository
        );

        hospitalRequestController = new HospitalRequestController(
                bloodRequestService,
                bloodBankRequestService
        );
    }

    private User createHospitalUser(String id, String name, Double lat, Double lon) {
        User u = new User();
        u.setId(id);
        u.setRole("hospital");
        u.setName(name);
        u.setEmail(id + "@hospital.org");
        u.setPasswordHash("");
        u.setPhone("9876543210");
        u.setLat(lat);
        u.setLng(lon);
        return u;
    }

    private BloodRequest createMockRequest(String id, String hospitalId, String bloodGroup, int units, String urgency, String status) {
        BloodRequest req = new BloodRequest();
        req.setId(id);
        req.setHospitalId(hospitalId);
        req.setPatientName("John Patient");
        req.setBloodGroup(bloodGroup);
        req.setUnits(units);
        req.setUrgency(urgency);
        req.setStatus(status);
        req.setSecuredUnits(0);
        req.setContact("9876543210");
        req.setRadius(15);
        req.setCreatedAt(LocalDateTime.now());
        return req;
    }

    @Test
    public void testContactNumberComesFromHospitalRegisteredAccountPhone() {
        User hosp = createHospitalUser("h1", "Apollo Hospital", 12.9716, 77.5946);
        hosp.setPhone("9123456789");
        when(userRepository.findById("h1")).thenReturn(Optional.of(hosp));
        when(bloodRequestRepository.save(any(BloodRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        CreateBloodRequestRequest req = new CreateBloodRequestRequest();
        req.setHospitalId("h1");
        req.setPatientName("Jane Doe");
        req.setBloodGroup("O+");
        req.setUnits(3);
        req.setUrgency("emergency");
        req.setContact("9988776655");
        req.setRadius(20);

        BloodRequest created = bloodRequestService.createRequest(req);

        assertNotNull(created);
        assertEquals("9123456789", created.getContact());
    }

    @Test
    public void testCreateEmergencyRequestSuccess() {
        User hosp = createHospitalUser("h1", "Apollo Hospital", 12.9716, 77.5946);
        when(userRepository.findById("h1")).thenReturn(Optional.of(hosp));
        when(bloodRequestRepository.save(any(BloodRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        CreateBloodRequestRequest req = new CreateBloodRequestRequest();
        req.setHospitalId("h1");
        req.setPatientName("Jane Doe");
        req.setBloodGroup("O+");
        req.setUnits(3);
        req.setUrgency("emergency");
        req.setContact("9988776655");
        req.setRadius(20);

        BloodRequest created = bloodRequestService.createRequest(req);

        assertNotNull(created);
        assertNotNull(created.getId());
        assertEquals("h1", created.getHospitalId());
        assertEquals("Jane Doe", created.getPatientName());
        assertEquals("O+", created.getBloodGroup());
        assertEquals(3, created.getUnits());
        assertEquals("emergency", created.getUrgency());
        assertEquals("open", created.getStatus());
        verify(bloodRequestRepository, times(1)).save(any(BloodRequest.class));
    }

    @Test
    public void testCreateScheduledRequestSuccess() {
        User hosp = createHospitalUser("h1", "Apollo Hospital", 12.9716, 77.5946);
        when(userRepository.findById("h1")).thenReturn(Optional.of(hosp));
        when(bloodRequestRepository.save(any(BloodRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        CreateBloodRequestRequest req = new CreateBloodRequestRequest();
        req.setHospitalId("h1");
        req.setPatientName("Scheduled Patient");
        req.setBloodGroup("A+");
        req.setUnits(2);
        req.setUrgency("scheduled");
        req.setRequestType("SCHEDULED");
        req.setReason("Scheduled Cardiac Surgery");
        req.setOperationTime(LocalDateTime.now().plusDays(3).toString());
        req.setAccumulationDeadline(LocalDateTime.now().plusDays(2).toString());
        req.setContact("9988776655");
        req.setRadius(25);

        BloodRequest created = bloodRequestService.createRequest(req);

        assertNotNull(created);
        assertEquals("scheduled", created.getUrgency());
        assertEquals("Scheduled Cardiac Surgery", created.getReason());
        assertNotNull(created.getOperationTime());
    }

    @Test
    public void testCreateRequestValidationMissingHospitalThrows() {
        CreateBloodRequestRequest req = new CreateBloodRequestRequest();
        req.setPatientName("Jane Doe");
        req.setBloodGroup("O+");
        req.setUnits(2);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            bloodRequestService.createRequest(req);
        });
        assertEquals("HOSPITAL_NOT_FOUND", ex.getMessage());
    }

    @Test
    public void testCreateRequestValidationInvalidBloodGroupThrows() {
        User hosp = createHospitalUser("h1", "City Hospital", 12.9716, 77.5946);
        when(userRepository.findById("h1")).thenReturn(Optional.of(hosp));

        CreateBloodRequestRequest req = new CreateBloodRequestRequest();
        req.setHospitalId("h1");
        req.setPatientName("Jane Doe");
        req.setBloodGroup("INVALID_GROUP");
        req.setUnits(2);
        req.setUrgency("emergency");
        req.setContact("9988776655");
        req.setRadius(20);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            bloodRequestService.createRequest(req);
        });
        assertTrue(ex.getMessage().contains("Blood group must be one of"));
    }

    @Test
    public void testCreateRequestUserNotAHospitalThrows() {
        User donor = new User();
        donor.setId("d1");
        donor.setRole("donor");
        donor.setName("Donor Dan");
        when(userRepository.findById("d1")).thenReturn(Optional.of(donor));

        CreateBloodRequestRequest req = new CreateBloodRequestRequest();
        req.setHospitalId("d1");
        req.setPatientName("Jane Doe");
        req.setBloodGroup("O+");
        req.setUnits(2);
        req.setUrgency("emergency");
        req.setContact("9988776655");
        req.setRadius(20);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            bloodRequestService.createRequest(req);
        });
        assertEquals("NOT_A_HOSPITAL", ex.getMessage());
    }

    @Test
    public void testGetRequestByIdSuccess() {
        BloodRequest req = createMockRequest("req-101", "h1", "B+", 4, "emergency", "open");
        when(bloodRequestRepository.findById("req-101")).thenReturn(Optional.of(req));
        when(requestBloodBankRepository.findByBloodRequestId("req-101")).thenReturn(new ArrayList<>());
        when(requestDonorRepository.findByBloodRequestId("req-101")).thenReturn(new ArrayList<>());

        HospitalRequestDTO dto = bloodRequestService.getRequestById("req-101");

        assertNotNull(dto);
        assertEquals("req-101", dto.getId());
        assertEquals("h1", dto.getHospitalId());
        assertEquals("B+", dto.getBloodGroup());
        assertEquals(4, dto.getUnits());
    }

    @Test
    public void testGetRequestByIdNotFoundThrowsException() {
        when(bloodRequestRepository.findById("unknown-req")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> {
            bloodRequestService.getRequestById("unknown-req");
        });
    }

    @Test
    public void testGetHospitalRequestsSuccess() {
        BloodRequest r1 = createMockRequest("req-1", "h1", "O+", 2, "emergency", "open");
        BloodRequest r2 = createMockRequest("req-2", "h1", "A-", 1, "scheduled", "fulfilled");
        when(userRepository.findById("h1")).thenReturn(Optional.of(createHospitalUser("h1", "City Hosp", 12.0, 77.0)));
        when(bloodRequestRepository.findByHospitalIdOrderByCreatedAtDesc("h1")).thenReturn(Arrays.asList(r1, r2));
        when(requestBloodBankRepository.findByBloodRequestId(anyString())).thenReturn(new ArrayList<>());
        when(requestDonorRepository.findByBloodRequestId(anyString())).thenReturn(new ArrayList<>());

        List<HospitalRequestDTO> list = bloodRequestService.getHospitalRequests("h1");

        assertNotNull(list);
        assertEquals(2, list.size());
        assertEquals("req-1", list.get(0).getId());
        assertEquals("req-2", list.get(1).getId());
    }

    @Test
    public void testGetTotalRequestCount() {
        when(bloodRequestRepository.count()).thenReturn(42L);

        long count = bloodRequestService.getTotalRequestCount();

        assertEquals(42L, count);
    }

    @Test
    public void testFulfillRequestSuccess() {
        BloodRequest req = createMockRequest("req-50", "h1", "AB+", 2, "emergency", "open");
        when(bloodRequestRepository.findById("req-50")).thenReturn(Optional.of(req));
        when(bloodRequestRepository.save(any(BloodRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        BloodRequest fulfilled = bloodRequestService.fulfillRequest("req-50", "h1");

        assertNotNull(fulfilled);
        assertEquals("fulfilled", fulfilled.getStatus());
        verify(bloodRequestRepository, times(1)).save(req);
    }

    @Test
    public void testFulfillRequestUnauthorizedHospitalThrows() {
        BloodRequest req = createMockRequest("req-50", "h1", "AB+", 2, "emergency", "open");
        when(bloodRequestRepository.findById("req-50")).thenReturn(Optional.of(req));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            bloodRequestService.fulfillRequest("req-50", "h2_intruder");
        });
        assertEquals("UNAUTHORIZED", ex.getMessage());
    }

    @Test
    public void testConfirmDonorSuccess() {
        BloodRequest req = createMockRequest("req-60", "h1", "O-", 2, "emergency", "open");
        req.setSecuredUnits(0);

        RequestDonor rd = new RequestDonor();
        rd.setDonorId("d1");
        rd.setStatus("accepted");

        when(bloodRequestRepository.findById("req-60")).thenReturn(Optional.of(req));
        when(requestDonorRepository.findByDonorIdAndBloodRequestId("d1", "req-60")).thenReturn(Optional.of(rd));
        when(requestBloodBankRepository.findByBloodRequestId("req-60")).thenReturn(new ArrayList<>());
        when(requestDonorRepository.findByBloodRequestId("req-60")).thenReturn(Arrays.asList(rd));
        when(bloodRequestRepository.save(any(BloodRequest.class))).thenAnswer(inv -> inv.getArgument(0));
        when(requestDonorRepository.save(any(RequestDonor.class))).thenAnswer(inv -> inv.getArgument(0));

        BloodRequest updated = bloodRequestService.confirmDonor("req-60", "d1", "h1");

        assertNotNull(updated);
        assertEquals(1, updated.getSecuredUnits());
        assertEquals("confirmed", rd.getStatus());
    }

    @Test
    public void testHospitalRequestDTOSafetyNoPasswordHashExposed() {
        BloodRequest req = createMockRequest("req-70", "h1", "A+", 2, "emergency", "open");
        when(bloodRequestRepository.findById("req-70")).thenReturn(Optional.of(req));
        when(requestBloodBankRepository.findByBloodRequestId("req-70")).thenReturn(new ArrayList<>());
        when(requestDonorRepository.findByBloodRequestId("req-70")).thenReturn(new ArrayList<>());

        HospitalRequestDTO dto = bloodRequestService.getRequestById("req-70");
        assertNotNull(dto);

        // Ensure fields are clean
        assertEquals("req-70", dto.getId());
        assertEquals("h1", dto.getHospitalId());
        assertEquals("John Patient", dto.getPatientName());
        assertEquals("A+", dto.getBloodGroup());
        assertEquals(2, dto.getUnits());
    }

    @Test
    public void testControllerGetRequestByIdEndpointSuccess() {
        BloodRequest req = createMockRequest("req-80", "h1", "B-", 1, "emergency", "open");
        when(bloodRequestRepository.findById("req-80")).thenReturn(Optional.of(req));
        when(requestBloodBankRepository.findByBloodRequestId("req-80")).thenReturn(new ArrayList<>());
        when(requestDonorRepository.findByBloodRequestId("req-80")).thenReturn(new ArrayList<>());

        ResponseEntity<?> response = hospitalRequestController.getRequestById("req-80");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody() instanceof HospitalRequestDTO);
        HospitalRequestDTO body = (HospitalRequestDTO) response.getBody();
        assertEquals("req-80", body.getId());
    }

    @Test
    public void testControllerGetTotalCountEndpoint() {
        when(bloodRequestRepository.count()).thenReturn(150L);

        ResponseEntity<?> response = hospitalRequestController.getTotalRequestCount();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody() instanceof Map);
        Map<?, ?> map = (Map<?, ?>) response.getBody();
        assertEquals(150L, map.get("count"));
    }

    @Test
    public void testRequestPopulatesAcceptedDonorDetailsForHospitalConfirmation() {
        User hospital = createHospitalUser("h1", "City Hospital", 16.5062, 80.6480);
        when(userRepository.findById("h1")).thenReturn(Optional.of(hospital));

        User donor = new User();
        donor.setId("d1");
        donor.setName("Ravi");
        donor.setRole("donor");
        donor.setBloodGroup("O+");
        donor.setPhone("9876543210");
        donor.setCity("Vijayawada");
        donor.setAddress("MG Road");
        donor.setLat(16.5100);
        donor.setLng(80.6500);
        when(userRepository.findById("d1")).thenReturn(Optional.of(donor));

        BloodRequest req = createMockRequest("req-1", "h1", "O+", 2, "emergency", "open");
        when(bloodRequestRepository.findById("req-1")).thenReturn(Optional.of(req));

        RequestDonor rd = new RequestDonor();
        rd.setBloodRequest(req);
        rd.setDonorId("d1");
        rd.setAlertType("direct");
        rd.setStatus("accepted");
        rd.setRespondedAt(LocalDateTime.now());

        when(requestDonorRepository.findByBloodRequestId("req-1")).thenReturn(Collections.singletonList(rd));
        when(requestBloodBankRepository.findByBloodRequestId("req-1")).thenReturn(Collections.emptyList());

        HospitalRequestDTO dto = bloodRequestService.getRequestById("req-1");
        assertNotNull(dto);
        assertEquals(1, dto.getDirectDonors().size());

        Map<String, Object> donorMap = dto.getDirectDonors().get(0);
        assertEquals("d1", donorMap.get("id"));
        assertEquals("Ravi", donorMap.get("name"));
        assertEquals("O+", donorMap.get("bloodGroup"));
        assertEquals("9876543210", donorMap.get("phone"));
        assertEquals("Vijayawada", donorMap.get("city"));
        assertEquals("MG Road", donorMap.get("address"));
        assertEquals("accepted", donorMap.get("status"));
        assertNotNull(donorMap.get("distance"));
    }

    @Test
    public void testConfirmDonorSetsGenderBasedRelaxationAndRecordsDonation_Male() {
        User hospital = createHospitalUser("h1", "City Hospital", 16.5062, 80.6480);
        when(userRepository.findById("h1")).thenReturn(Optional.of(hospital));

        User donor = new User();
        donor.setId("d1");
        donor.setName("Ravi");
        donor.setRole("donor");
        donor.setGender("MALE");
        donor.setBloodGroup("O+");
        donor.setAvailable(true);
        when(userRepository.findById("d1")).thenReturn(Optional.of(donor));

        BloodRequest req = createMockRequest("req-1", "h1", "O+", 2, "emergency", "open");
        when(bloodRequestRepository.findById("req-1")).thenReturn(Optional.of(req));

        RequestDonor rd = new RequestDonor();
        rd.setBloodRequest(req);
        rd.setDonorId("d1");
        rd.setAlertType("direct");
        rd.setStatus("accepted");
        rd.setRespondedAt(LocalDateTime.now());

        when(requestDonorRepository.findByDonorIdAndBloodRequestId("d1", "req-1")).thenReturn(Optional.of(rd));
        when(requestDonorRepository.findByBloodRequestId("req-1")).thenReturn(Collections.singletonList(rd));
        when(requestBloodBankRepository.findByBloodRequestId("req-1")).thenReturn(Collections.emptyList());
        when(bloodRequestRepository.save(any(BloodRequest.class))).thenAnswer(i -> i.getArgument(0));

        BloodRequest result = bloodRequestService.confirmDonor("req-1", "d1", "h1");
        assertNotNull(result);
        assertEquals(1, result.getSecuredUnits());

        // Verify donor last donation and next eligible date (+84 days for MALE)
        java.time.LocalDate today = java.time.LocalDate.now();
        assertEquals(today.toString(), donor.getLastDonation());
        assertEquals(today.plusDays(84).toString(), donor.getNextEligibleDate());
        assertFalse(donor.isEligibleToDonate(), "Male donor must be temporarily ineligible within 84-day relaxation period");

        // Verify donation history record was saved in MySQL
        verify(donationHistoryRepository, times(1)).save(any(com.bloodlink.entity.DonationHistory.class));
        verify(userRepository, atLeastOnce()).save(donor);
    }

    @Test
    public void testConfirmDonorSetsGenderBasedRelaxationAndRecordsDonation_Female() {
        User hospital = createHospitalUser("h2", "Apex Hospital", 16.5062, 80.6480);
        when(userRepository.findById("h2")).thenReturn(Optional.of(hospital));

        User donor = new User();
        donor.setId("d2");
        donor.setName("Priya");
        donor.setRole("donor");
        donor.setGender("FEMALE");
        donor.setBloodGroup("A+");
        donor.setAvailable(true);
        when(userRepository.findById("d2")).thenReturn(Optional.of(donor));

        BloodRequest req = createMockRequest("req-2", "h2", "A+", 1, "emergency", "open");
        when(bloodRequestRepository.findById("req-2")).thenReturn(Optional.of(req));

        RequestDonor rd = new RequestDonor();
        rd.setBloodRequest(req);
        rd.setDonorId("d2");
        rd.setAlertType("direct");
        rd.setStatus("accepted");
        rd.setRespondedAt(LocalDateTime.now());

        when(requestDonorRepository.findByDonorIdAndBloodRequestId("d2", "req-2")).thenReturn(Optional.of(rd));
        when(requestDonorRepository.findByBloodRequestId("req-2")).thenReturn(Collections.singletonList(rd));
        when(requestBloodBankRepository.findByBloodRequestId("req-2")).thenReturn(Collections.emptyList());
        when(bloodRequestRepository.save(any(BloodRequest.class))).thenAnswer(i -> i.getArgument(0));

        BloodRequest result = bloodRequestService.confirmDonor("req-2", "d2", "h2");
        assertNotNull(result);
        assertEquals(1, result.getSecuredUnits());

        // Verify female donor last donation and next eligible date (+112 days for FEMALE)
        java.time.LocalDate today = java.time.LocalDate.now();
        assertEquals(today.toString(), donor.getLastDonation());
        assertEquals(today.plusDays(112).toString(), donor.getNextEligibleDate());
        assertFalse(donor.isEligibleToDonate(), "Female donor must be temporarily ineligible within 112-day relaxation period");
    }

    @Test
    public void testMultipleAcceptedDonorsReRankedByActualDistance() {
        User hospital = createHospitalUser("h1", "City Hospital", 16.5062, 80.6480);
        when(userRepository.findById("h1")).thenReturn(Optional.of(hospital));

        // Donor 1: accepted, farther away (e.g. 10 km)
        User d1 = new User();
        d1.setId("d1");
        d1.setName("Far Accepted Donor");
        d1.setRole("donor");
        d1.setBloodGroup("O+");
        d1.setLat(16.5900);
        d1.setLng(80.6500);

        // Donor 2: accepted, closer (e.g. 2 km)
        User d2 = new User();
        d2.setId("d2");
        d2.setName("Close Accepted Donor");
        d2.setRole("donor");
        d2.setBloodGroup("O+");
        d2.setLat(16.5200);
        d2.setLng(80.6500);

        // Donor 3: pending
        User d3 = new User();
        d3.setId("d3");
        d3.setName("Pending Donor");
        d3.setRole("donor");
        d3.setBloodGroup("O+");
        d3.setLat(16.5100);
        d3.setLng(80.6500);

        when(userRepository.findById("d1")).thenReturn(Optional.of(d1));
        when(userRepository.findById("d2")).thenReturn(Optional.of(d2));
        when(userRepository.findById("d3")).thenReturn(Optional.of(d3));

        BloodRequest req = createMockRequest("req-1", "h1", "O+", 2, "emergency", "open");
        when(bloodRequestRepository.findById("req-1")).thenReturn(Optional.of(req));

        RequestDonor rd1 = new RequestDonor();
        rd1.setBloodRequest(req);
        rd1.setDonorId("d1");
        rd1.setAlertType("direct");
        rd1.setStatus("accepted");

        RequestDonor rd2 = new RequestDonor();
        rd2.setBloodRequest(req);
        rd2.setDonorId("d2");
        rd2.setAlertType("direct");
        rd2.setStatus("accepted");

        RequestDonor rd3 = new RequestDonor();
        rd3.setBloodRequest(req);
        rd3.setDonorId("d3");
        rd3.setAlertType("direct");
        rd3.setStatus("pending");

        when(requestDonorRepository.findByBloodRequestId("req-1")).thenReturn(Arrays.asList(rd1, rd3, rd2));
        when(requestBloodBankRepository.findByBloodRequestId("req-1")).thenReturn(Collections.emptyList());

        HospitalRequestDTO dto = bloodRequestService.getRequestById("req-1");
        assertNotNull(dto);
        assertEquals(3, dto.getDirectDonors().size());

        // First should be the closest accepted donor (d2)
        assertEquals("d2", dto.getDirectDonors().get(0).get("id"));
        assertEquals("accepted", dto.getDirectDonors().get(0).get("status"));
        assertNotNull(dto.getDirectDonors().get(0).get("rankScore"));

        // Second should be farther accepted donor (d1)
        assertEquals("d1", dto.getDirectDonors().get(1).get("id"));
        assertEquals("accepted", dto.getDirectDonors().get(1).get("status"));

        // Third should be pending donor (d3)
        assertEquals("d3", dto.getDirectDonors().get(2).get("id"));
        assertEquals("pending", dto.getDirectDonors().get(2).get("status"));
    }
}