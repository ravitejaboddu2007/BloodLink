package com.bloodlink.security;

import com.bloodlink.controller.*;
import com.bloodlink.dto.*;
import com.bloodlink.entity.*;
import com.bloodlink.repository.*;
import com.bloodlink.service.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class ResourceOwnershipSecurityTest {

    private AuthenticatedUserService authenticatedUserService;
    private UserRepository userRepository;
    private BloodRequestRepository bloodRequestRepository;
    private RequestDonorRepository requestDonorRepository;
    private RequestBloodBankRepository requestBloodBankRepository;
    private InventoryRepository inventoryRepository;
    private DonationHistoryRepository donationHistoryRepository;
    private ReviewRepository reviewRepository;

    private UserService userService;
    private DonorService donorService;
    private DonationHistoryService donationHistoryService;
    private BloodRequestService bloodRequestService;
    private BloodBankRequestService bloodBankRequestService;
    private InventoryService inventoryService;
    private ReviewService reviewService;

    private UserController userController;
    private DonorController donorController;
    private HospitalController hospitalController;
    private HospitalRequestController hospitalRequestController;
    private BloodBankAlertController bloodBankAlertController;
    private BloodBankInventoryController bloodBankInventoryController;
    private ReviewController reviewController;

    @BeforeEach
    public void setUp() {
        SecurityContextHolder.clearContext();
        authenticatedUserService = new AuthenticatedUserService();

        userRepository = Mockito.mock(UserRepository.class);
        bloodRequestRepository = Mockito.mock(BloodRequestRepository.class);
        requestDonorRepository = Mockito.mock(RequestDonorRepository.class);
        requestBloodBankRepository = Mockito.mock(RequestBloodBankRepository.class);
        inventoryRepository = Mockito.mock(InventoryRepository.class);
        donationHistoryRepository = Mockito.mock(DonationHistoryRepository.class);
        reviewRepository = Mockito.mock(ReviewRepository.class);

        userService = Mockito.mock(UserService.class);
        donorService = Mockito.mock(DonorService.class);
        donationHistoryService = Mockito.mock(DonationHistoryService.class);
        bloodRequestService = Mockito.mock(BloodRequestService.class);
        bloodBankRequestService = Mockito.mock(BloodBankRequestService.class);
        inventoryService = Mockito.mock(InventoryService.class);
        reviewService = Mockito.mock(ReviewService.class);

        userController = new UserController(userService, authenticatedUserService);
        donorController = new DonorController(donorService, donationHistoryService, authenticatedUserService);
        hospitalController = new HospitalController(bloodRequestService, authenticatedUserService);
        hospitalRequestController = new HospitalRequestController(
                bloodRequestService, bloodBankRequestService, bloodRequestRepository,
                requestDonorRepository, requestBloodBankRepository, authenticatedUserService
        );
        bloodBankAlertController = new BloodBankAlertController(bloodBankRequestService, authenticatedUserService);
        bloodBankInventoryController = new BloodBankInventoryController(inventoryService, authenticatedUserService);
        reviewController = new ReviewController(reviewService, authenticatedUserService);
    }

    @AfterEach
    public void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void setSecurityContext(String userId, String role) {
        if (userId == null) {
            SecurityContextHolder.clearContext();
            return;
        }
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                userId,
                null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    // TEST A: User A JWT -> GET /api/users/A -> allowed
    @Test
    public void testA_UserA_GetProfileA_Allowed() {
        setSecurityContext("u_A", "donor");
        when(userService.getUserProfile("u_A")).thenReturn(new UserProfileDTO());

        ResponseEntity<?> response = userController.getUserProfile("u_A");
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    // TEST B: User A JWT -> GET /api/users/B -> 403
    @Test
    public void testB_UserA_GetProfileB_Forbidden() {
        setSecurityContext("u_A", "donor");

        ResponseEntity<?> response = userController.getUserProfile("u_B");
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    // TEST C: Donor A JWT -> PUT /api/donors/A/availability -> allowed
    @Test
    public void testC_DonorA_UpdateAvailabilityA_Allowed() {
        setSecurityContext("d_A", "donor");
        when(donorService.updateAvailability("d_A", true)).thenReturn(Collections.singletonMap("success", true));

        AvailabilityRequest req = new AvailabilityRequest();
        req.setAvailable(true);
        ResponseEntity<?> response = donorController.updateAvailability("d_A", req);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    // TEST D: Donor A JWT -> PUT /api/donors/B/availability -> 403
    @Test
    public void testD_DonorA_UpdateAvailabilityB_Forbidden() {
        setSecurityContext("d_A", "donor");

        AvailabilityRequest req = new AvailabilityRequest();
        req.setAvailable(true);
        ResponseEntity<?> response = donorController.updateAvailability("d_B", req);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    // TEST E: Donor A JWT -> GET /api/donors/B/history -> 403
    @Test
    public void testE_DonorA_GetHistoryB_Forbidden() {
        setSecurityContext("d_A", "donor");

        ResponseEntity<?> response = donorController.getDonationHistory("d_B");
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    // TEST F: Donor A JWT -> PUT /api/donors/B/location -> 403
    @Test
    public void testF_DonorA_UpdateLocationB_Forbidden() {
        setSecurityContext("d_A", "donor");

        LocationUpdateRequest req = new LocationUpdateRequest();
        req.setLat(12.9716);
        req.setLng(77.5946);
        ResponseEntity<?> response = donorController.updateLocation("d_B", req);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    // TEST G: Hospital A JWT -> GET /api/hospitals/A/requests -> allowed
    @Test
    public void testG_HospitalA_GetRequestsA_Allowed() {
        setSecurityContext("h_A", "hospital");
        when(bloodRequestService.getHospitalRequests("h_A")).thenReturn(Collections.emptyList());

        ResponseEntity<?> response = hospitalController.getHospitalRequests("h_A");
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    // TEST H: Hospital A JWT -> GET /api/hospitals/B/requests -> 403
    @Test
    public void testH_HospitalA_GetRequestsB_Forbidden() {
        setSecurityContext("h_A", "hospital");

        ResponseEntity<?> response = hospitalController.getHospitalRequests("h_B");
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    // TEST I: Hospital A JWT -> GET /api/requests/hospital/B -> 403
    @Test
    public void testI_HospitalA_GetHospitalRequestsB_Forbidden() {
        setSecurityContext("h_A", "hospital");

        ResponseEntity<?> response = hospitalRequestController.getHospitalRequests("h_B");
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    // TEST J: Hospital A JWT -> operate on request owned by A -> allowed
    @Test
    public void testJ_HospitalA_OperateRequestA_Allowed() {
        setSecurityContext("h_A", "hospital");

        BloodRequest r = new BloodRequest();
        r.setId("req_100");
        r.setHospitalId("h_A");
        when(bloodRequestRepository.findById("req_100")).thenReturn(Optional.of(r));
        when(bloodRequestService.matchDonorsForRequest("req_100")).thenReturn(new MatchDonorsResponseDTO());

        ResponseEntity<?> response = hospitalRequestController.matchDonorsForRequest("req_100");
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    // TEST K: Hospital A JWT -> operate on request owned by B -> 403
    @Test
    public void testK_HospitalA_OperateRequestB_Forbidden() {
        setSecurityContext("h_A", "hospital");

        BloodRequest r = new BloodRequest();
        r.setId("req_200");
        r.setHospitalId("h_B");
        when(bloodRequestRepository.findById("req_200")).thenReturn(Optional.of(r));

        ResponseEntity<?> response = hospitalRequestController.matchDonorsForRequest("req_200");
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    // TEST L: Hospital A JWT -> request body contains hospitalId=B -> must NOT create as Hospital B
    @Test
    public void testL_HospitalA_CreateRequestWithHospitalBInBody_Forbidden() {
        setSecurityContext("h_A", "hospital");

        CreateBloodRequestRequest req = new CreateBloodRequestRequest();
        req.setHospitalId("h_B");
        req.setBloodGroup("A+");
        req.setUnits(2);

        ResponseEntity<?> response = hospitalRequestController.createRequest(req);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    // TEST M: Blood Bank A JWT -> PUT /api/bloodbanks/A/inventory -> allowed
    @Test
    public void testM_BloodBankA_UpdateInventoryA_Allowed() {
        setSecurityContext("b_A", "bloodbank");
        when(inventoryService.updateInventory(eq("b_A"), any())).thenReturn(new InventoryDTO());

        UpdateInventoryRequest req = new UpdateInventoryRequest();
        ResponseEntity<?> response = bloodBankInventoryController.updateInventory("b_A", req);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    // TEST N: Blood Bank A JWT -> PUT /api/bloodbanks/B/inventory -> 403
    @Test
    public void testN_BloodBankA_UpdateInventoryB_Forbidden() {
        setSecurityContext("b_A", "bloodbank");

        UpdateInventoryRequest req = new UpdateInventoryRequest();
        ResponseEntity<?> response = bloodBankInventoryController.updateInventory("b_B", req);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    // TEST O: Blood Bank A JWT -> GET /api/bloodbanks/B/alerts -> 403
    @Test
    public void testO_BloodBankA_GetAlertsB_Forbidden() {
        setSecurityContext("b_A", "bloodbank");

        ResponseEntity<?> response = bloodBankAlertController.getAlerts("b_B");
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    // TEST P: User A JWT -> DELETE User B's review -> 403
    @Test
    public void testP_UserA_DeleteReviewB_Forbidden() {
        setSecurityContext("u_A", "donor");

        ResponseEntity<?> response = reviewController.deleteReview("rev_1", "u_B");
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    // TEST Q: User A JWT -> DELETE /api/reviews/user/B -> 403
    @Test
    public void testQ_UserA_DeleteReviewByUserB_Forbidden() {
        setSecurityContext("u_A", "donor");

        ResponseEntity<?> response = reviewController.deleteReviewByUser("u_B");
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    // TEST R: User A JWT -> create review while request body says userId=B -> 403
    @Test
    public void testR_UserA_CreateReviewBodyB_Forbidden() {
        setSecurityContext("u_A", "donor");

        ReviewRequest req = new ReviewRequest();
        req.setUserId("u_B");
        req.setRating(5);
        req.setText("Great app");

        ResponseEntity<?> response = reviewController.submitReview(req);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    // TEST S: Donor A JWT -> respond to Donor B's alert -> 403
    @Test
    public void testS_DonorA_RespondAlertB_Forbidden() {
        setSecurityContext("d_A", "donor");

        AlertResponseRequest req = new AlertResponseRequest();
        req.setStatus("accepted");
        ResponseEntity<?> response = donorController.respondToAlert("d_B", "req_1", req);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    // TEST T: Blood Bank A JWT -> respond to Blood Bank B's alert -> 403
    @Test
    public void testT_BloodBankA_RespondAlertB_Forbidden() {
        setSecurityContext("b_A", "bloodbank");

        BloodBankResponseRequest req = new BloodBankResponseRequest();
        req.setStatus("accepted");
        ResponseEntity<?> response = bloodBankAlertController.respondAlert("b_B", "req_1", req);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    // TEST U: Missing JWT -> access protected resource -> 403/401
    @Test
    public void testU_MissingJwt_ForbiddenOrUnauthorized() {
        setSecurityContext(null, null);
        assertNull(authenticatedUserService.getAuthenticatedUserId());
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> {
            authenticatedUserService.requireAuthenticatedUserId();
        });
    }

    // TEST V: Valid JWT + wrong role -> 403
    @Test
    public void testV_DonorAccessingHospitalController_Forbidden() {
        setSecurityContext("d_A", "donor");

        ResponseEntity<?> response = hospitalController.getHospitalRequests("h_A");
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    // TEST W: Valid JWT + correct role + correct ownership -> reaches business logic
    @Test
    public void testW_ValidJwtCorrectRoleCorrectOwnership_ReachesLogic() {
        setSecurityContext("d_A", "donor");
        when(donorService.getDonorAlerts("d_A")).thenReturn(Collections.emptyList());

        ResponseEntity<?> response = donorController.getDonorAlerts("d_A");
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(donorService, times(1)).getDonorAlerts("d_A");
    }
}
