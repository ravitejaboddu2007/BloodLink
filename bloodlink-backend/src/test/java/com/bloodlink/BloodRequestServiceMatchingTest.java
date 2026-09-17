package com.bloodlink;

import com.bloodlink.dto.CreateDonorAlertsRequest;
import com.bloodlink.dto.MatchDonorsResponseDTO;
import com.bloodlink.dto.MatchedDonorItemDTO;
import com.bloodlink.entity.BloodRequest;
import com.bloodlink.entity.RequestDonor;
import com.bloodlink.entity.User;
import com.bloodlink.repository.BloodRequestRepository;
import com.bloodlink.repository.DonationHistoryRepository;
import com.bloodlink.repository.RequestBloodBankRepository;
import com.bloodlink.repository.RequestDonorRepository;
import com.bloodlink.repository.UserRepository;
import com.bloodlink.service.BloodRequestService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

public class BloodRequestServiceMatchingTest {

    private BloodRequestRepository bloodRequestRepository;
    private RequestDonorRepository requestDonorRepository;
    private RequestBloodBankRepository requestBloodBankRepository;
    private DonationHistoryRepository donationHistoryRepository;
    private UserRepository userRepository;
    private BloodRequestService bloodRequestService;

    @BeforeEach
    public void setUp() {
        bloodRequestRepository = Mockito.mock(BloodRequestRepository.class);
        requestDonorRepository = Mockito.mock(RequestDonorRepository.class);
        requestBloodBankRepository = Mockito.mock(RequestBloodBankRepository.class);
        donationHistoryRepository = Mockito.mock(DonationHistoryRepository.class);
        userRepository = Mockito.mock(UserRepository.class);

        bloodRequestService = new BloodRequestService(
                bloodRequestRepository,
                requestDonorRepository,
                requestBloodBankRepository,
                donationHistoryRepository,
                userRepository
        );
    }

    private User createDonor(String id, String name, String bloodGroup, Double lat, Double lng, LocalDateTime updated, boolean available) {
        User u = new User();
        u.setId(id);
        u.setName(name);
        u.setRole("donor");
        u.setBloodGroup(bloodGroup);
        u.setPhone("9876543210");
        u.setCity("TestCity");
        u.setState("TestState");
        u.setLat(lat);
        u.setLng(lng);
        u.setRegisteredLat(lat);
        u.setRegisteredLng(lng);
        u.setLocationUpdatedAt(updated);
        u.setAvailable(available);
        return u;
    }

    private BloodRequest createRequest(String id, String hospitalId, String bloodGroup, Integer radius) {
        BloodRequest br = new BloodRequest();
        br.setId(id);
        br.setHospitalId(hospitalId);
        br.setBloodGroup(bloodGroup);
        br.setUnits(3);
        br.setRadius(radius);
        br.setStatus("open");
        return br;
    }

    @Test
    public void testCompatibilityFilter() {
        User hospital = new User();
        hospital.setId("hosp1");
        hospital.setRole("hospital");
        hospital.setLat(28.6139);
        hospital.setLng(77.2090);

        when(userRepository.findById("hosp1")).thenReturn(Optional.of(hospital));

        BloodRequest req = createRequest("req_Apos", "hosp1", "A+", 15);
        when(bloodRequestRepository.findById("req_Apos")).thenReturn(Optional.of(req));

        // Donors: A+ (compat), A- (compat), O+ (compat), O- (compat), B+ (incompat), AB+ (incompat)
        LocalDateTime fresh = LocalDateTime.now().minusMinutes(30);
        User d1 = createDonor("d1", "Donor Apos", "A+", 28.62, 77.21, fresh, true);
        User d2 = createDonor("d2", "Donor Aneg", "A-", 28.63, 77.21, fresh, true);
        User d3 = createDonor("d3", "Donor Opos", "O+", 28.64, 77.21, fresh, true);
        User d4 = createDonor("d4", "Donor Oneg", "O-", 28.65, 77.21, fresh, true);
        User d5 = createDonor("d5", "Donor Bpos", "B+", 28.62, 77.21, fresh, true);
        User d6 = createDonor("d6", "Donor ABpos", "AB+", 28.62, 77.21, fresh, true);

        when(userRepository.findByRoleIgnoreCaseAndAvailableTrue("donor"))
                .thenReturn(Arrays.asList(d1, d2, d3, d4, d5, d6));

        MatchDonorsResponseDTO result = bloodRequestService.matchDonorsForRequest("req_Apos");

        assertNotNull(result);
        assertEquals(4, result.getTotalMatched());
        assertEquals(3, result.getDirect().size());
        assertEquals(1, result.getWaiting().size());

        List<String> matchedIds = new ArrayList<>();
        result.getDirect().forEach(d -> matchedIds.add(d.getId()));
        result.getWaiting().forEach(d -> matchedIds.add(d.getId()));

        assertTrue(matchedIds.contains("d1"));
        assertTrue(matchedIds.contains("d2"));
        assertTrue(matchedIds.contains("d3"));
        assertTrue(matchedIds.contains("d4"));
        assertFalse(matchedIds.contains("d5")); // B+ excluded
        assertFalse(matchedIds.contains("d6")); // AB+ excluded
    }

    @Test
    public void testFreshnessAndRanking() {
        User hospital = new User();
        hospital.setId("hosp1");
        hospital.setRole("hospital");
        hospital.setLat(28.6139);
        hospital.setLng(77.2090);

        when(userRepository.findById("hosp1")).thenReturn(Optional.of(hospital));

        BloodRequest req = createRequest("req_rank", "hosp1", "O+", 30);
        when(bloodRequestRepository.findById("req_rank")).thenReturn(Optional.of(req));

        // Donor 1: Very close (1km away), but very old location (>24h) -> Penalty +8km -> Score = 1 + 8 = 9km
        User d1 = createDonor("d1", "Close Old", "O+", 28.6229, 77.2090, LocalDateTime.now().minusHours(30), true);

        // Donor 2: Farther (3km away), but fresh location (<2h) -> Penalty 0km -> Score = 3 + 0 = 3km
        User d2 = createDonor("d2", "Far Fresh", "O+", 28.6409, 77.2090, LocalDateTime.now().minusMinutes(10), true);

        when(userRepository.findByRoleIgnoreCaseAndAvailableTrue("donor"))
                .thenReturn(Arrays.asList(d1, d2));

        MatchDonorsResponseDTO result = bloodRequestService.matchDonorsForRequest("req_rank");

        assertNotNull(result);
        assertEquals(2, result.getDirect().size());
        // Donor 2 (score 3.0) should be ranked before Donor 1 (score 9.0)
        assertEquals("d2", result.getDirect().get(0).getId());
        assertEquals("d1", result.getDirect().get(1).getId());
    }

    @Test
    public void testRadiusExpansion() {
        User hospital = new User();
        hospital.setId("hosp1");
        hospital.setRole("hospital");
        hospital.setLat(28.6139);
        hospital.setLng(77.2090);

        when(userRepository.findById("hosp1")).thenReturn(Optional.of(hospital));

        // Request with initial radius 10km
        BloodRequest req = createRequest("req_exp", "hosp1", "O-", 10);
        when(bloodRequestRepository.findById("req_exp")).thenReturn(Optional.of(req));

        // Only 1 donor within 10km, but 2 more within 20km (expanded radius)
        LocalDateTime fresh = LocalDateTime.now().minusMinutes(5);
        User d1 = createDonor("d1", "Donor 5km", "O-", 28.65, 77.209, fresh, true); // ~4km
        User d2 = createDonor("d2", "Donor 15km", "O-", 28.74, 77.209, fresh, true); // ~14km
        User d3 = createDonor("d3", "Donor 18km", "O-", 28.77, 77.209, fresh, true); // ~17km
        User d4 = createDonor("d4", "Donor 60km", "O-", 29.15, 77.209, fresh, true); // ~60km (beyond 50km max)

        when(userRepository.findByRoleIgnoreCaseAndAvailableTrue("donor"))
                .thenReturn(Arrays.asList(d1, d2, d3, d4));

        MatchDonorsResponseDTO result = bloodRequestService.matchDonorsForRequest("req_exp");

        assertNotNull(result);
        assertFalse(result.getExpanded());
        assertEquals(10, result.getRadiusUsed());
        assertEquals(1, result.getTotalMatched());
        assertEquals(1, result.getDirect().size());
    }

    @Test
    public void testTwoTierSegmentation() {
        User hospital = new User();
        hospital.setId("hosp1");
        hospital.setRole("hospital");
        hospital.setLat(28.6139);
        hospital.setLng(77.2090);

        when(userRepository.findById("hosp1")).thenReturn(Optional.of(hospital));

        BloodRequest req = createRequest("req_12donors", "hosp1", "AB+", 50);
        when(bloodRequestRepository.findById("req_12donors")).thenReturn(Optional.of(req));

        List<User> twelveDonors = new ArrayList<>();
        LocalDateTime fresh = LocalDateTime.now().minusMinutes(5);
        for (int i = 1; i <= 12; i++) {
            twelveDonors.add(createDonor("d" + i, "Donor " + i, "AB+", 28.6139 + (i * 0.01), 77.2090, fresh, true));
        }

        when(userRepository.findByRoleIgnoreCaseAndAvailableTrue("donor"))
                .thenReturn(twelveDonors);

        MatchDonorsResponseDTO result = bloodRequestService.matchDonorsForRequest("req_12donors");

        assertNotNull(result);
        assertEquals(12, result.getTotalMatched());
        assertEquals(3, result.getDirect().size(), "Top 3 should be in direct tier");
        assertEquals(7, result.getWaiting().size(), "Next 7 should be in waiting tier");

        assertEquals("direct", result.getDirect().get(0).getType());
        assertEquals("direct", result.getDirect().get(1).getType());
        assertEquals("direct", result.getDirect().get(2).getType());

        assertEquals("waiting", result.getWaiting().get(0).getType());
        assertEquals("waiting", result.getWaiting().get(6).getType());
    }

    @Test
    public void testIneligibleDonorsIn90DayRelaxationPeriodExcludedFromMatching() {
        User hospital = new User();
        hospital.setId("hosp1");
        hospital.setRole("hospital");
        hospital.setLat(28.6139);
        hospital.setLng(77.2090);

        when(userRepository.findById("hosp1")).thenReturn(Optional.of(hospital));

        BloodRequest req = createRequest("req_eligibility", "hosp1", "A+", 20);
        when(bloodRequestRepository.findById("req_eligibility")).thenReturn(Optional.of(req));

        LocalDateTime fresh = LocalDateTime.now().minusMinutes(5);
        
        // Donor 1: Eligible (last donation past resting period: MALE 84 days / FEMALE 112 days)
        User d1 = createDonor("d1", "Eligible Donor", "A+", 28.62, 77.21, fresh, true);
        d1.setLastDonation(java.time.LocalDate.now().minusDays(95).toString());
        d1.setNextEligibleDate(java.time.LocalDate.now().minusDays(5).toString());

        // Donor 2: Ineligible (donated 10 days ago, next eligible in 80 days)
        User d2 = createDonor("d2", "Recent Donor", "A+", 28.62, 77.21, fresh, true);
        d2.setLastDonation(java.time.LocalDate.now().minusDays(10).toString());
        d2.setNextEligibleDate(java.time.LocalDate.now().plusDays(80).toString());

        // Donor 3: Ineligible (no nextEligibleDate, but lastDonation is 20 days ago)
        User d3 = createDonor("d3", "Another Recent Donor", "A+", 28.62, 77.21, fresh, true);
        d3.setLastDonation(java.time.LocalDate.now().minusDays(20).toString());

        when(userRepository.findByRoleIgnoreCaseAndAvailableTrue("donor"))
                .thenReturn(Arrays.asList(d1, d2, d3));

        MatchDonorsResponseDTO result = bloodRequestService.matchDonorsForRequest("req_eligibility");

        assertNotNull(result);
        assertEquals(1, result.getTotalMatched(), "Only the eligible donor should be matched");
        assertEquals("d1", result.getDirect().get(0).getId());
    }

    @Test
    public void test2HourCooldownSoftPenaltyRanking() {
        User hospital = new User();
        hospital.setId("hosp1");
        hospital.setRole("hospital");
        hospital.setLat(28.6139);
        hospital.setLng(77.2090);

        when(userRepository.findById("hosp1")).thenReturn(Optional.of(hospital));

        BloodRequest req = createRequest("req_cooldown", "hosp1", "O+", 30);
        when(bloodRequestRepository.findById("req_cooldown")).thenReturn(Optional.of(req));

        LocalDateTime fresh = LocalDateTime.now().minusMinutes(5);

        // Donor B: Closer (2km away), but notified 1 hour ago (within 2-hour cooldown) -> Rank score = 2 * 1.5 + 0 + 15 = 18.0
        User dB = createDonor("dB", "Closer Notified Recently", "O+", 28.6319, 77.2090, fresh, true);
        dB.setLastNotifiedAt(LocalDateTime.now().minusHours(1));

        // Donor A: Farther (3km away), not recently notified -> Rank score = 3 * 1.5 + 0 + 0 = 4.5
        User dA = createDonor("dA", "Farther Not Notified", "O+", 28.6409, 77.2090, fresh, true);
        dA.setLastNotifiedAt(null);

        when(userRepository.findByRoleIgnoreCaseAndAvailableTrue("donor"))
                .thenReturn(Arrays.asList(dB, dA));

        MatchDonorsResponseDTO result = bloodRequestService.matchDonorsForRequest("req_cooldown");

        assertNotNull(result);
        assertEquals(2, result.getTotalMatched(), "Both donors must be included (soft penalty, not hard exclusion)");
        assertEquals("dA", result.getDirect().get(0).getId(), "Donor A (score 4.5) should be ranked before Donor B (score 18.0)");
        assertEquals("dB", result.getDirect().get(1).getId(), "Donor B is still present as second choice");
    }

    @Test
    public void testCooldownExpiresAfter2Hours() {
        User hospital = new User();
        hospital.setId("hosp1");
        hospital.setRole("hospital");
        hospital.setLat(28.6139);
        hospital.setLng(77.2090);

        when(userRepository.findById("hosp1")).thenReturn(Optional.of(hospital));

        BloodRequest req = createRequest("req_expired_cd", "hosp1", "O+", 30);
        when(bloodRequestRepository.findById("req_expired_cd")).thenReturn(Optional.of(req));

        LocalDateTime fresh = LocalDateTime.now().minusMinutes(5);

        // Donor B: Closer (2km away), notified 2.5 hours ago (> 2h cooldown expired) -> Rank score = 2 * 1.5 + 0 + 0 = 3.0
        User dB = createDonor("dB", "Closer Cooldown Expired", "O+", 28.6319, 77.2090, fresh, true);
        dB.setLastNotifiedAt(LocalDateTime.now().minusMinutes(150));

        // Donor A: Farther (3km away), not recently notified -> Rank score = 3 * 1.5 + 0 + 0 = 4.5
        User dA = createDonor("dA", "Farther Not Notified", "O+", 28.6409, 77.2090, fresh, true);
        dA.setLastNotifiedAt(null);

        when(userRepository.findByRoleIgnoreCaseAndAvailableTrue("donor"))
                .thenReturn(Arrays.asList(dA, dB));

        MatchDonorsResponseDTO result = bloodRequestService.matchDonorsForRequest("req_expired_cd");

        assertNotNull(result);
        assertEquals(2, result.getTotalMatched());
        assertEquals("dB", result.getDirect().get(0).getId(), "Donor B (score 3.0) should now be ranked first after cooldown expired");
        assertEquals("dA", result.getDirect().get(1).getId(), "Donor A (score 4.5) should be ranked second");
    }

    @Test
    public void testCreateDonorAlertsUpdatesLastNotifiedAt() {
        BloodRequest br = createRequest("req_alert_cd", "hosp1", "O+", 15);
        when(bloodRequestRepository.findById("req_alert_cd")).thenReturn(Optional.of(br));

        User donor = createDonor("d_test", "Alerted Donor", "O+", 28.62, 77.21, LocalDateTime.now(), true);
        when(userRepository.findById("d_test")).thenReturn(Optional.of(donor));
        when(requestDonorRepository.findByDonorIdAndBloodRequestId("d_test", "req_alert_cd")).thenReturn(Optional.empty());
        when(requestDonorRepository.save(Mockito.any(RequestDonor.class))).thenAnswer(inv -> inv.getArgument(0));

        com.bloodlink.dto.CreateDonorAlertsRequest req = new com.bloodlink.dto.CreateDonorAlertsRequest();
        com.bloodlink.dto.CreateDonorAlertsRequest.DonorAlertItem item = new com.bloodlink.dto.CreateDonorAlertsRequest.DonorAlertItem();
        item.setDonorId("d_test");
        item.setAlertType("direct");
        req.setDonors(Collections.singletonList(item));

        assertNull(donor.getLastNotifiedAt(), "Donor lastNotifiedAt should be null initially");

        List<RequestDonor> created = bloodRequestService.createDonorAlerts("req_alert_cd", req);

        assertEquals(1, created.size());
        assertNotNull(donor.getLastNotifiedAt(), "Donor lastNotifiedAt must be updated when alert is created");
        Mockito.verify(userRepository).save(donor);
    }

    @Test
    public void testSuspendedDonorExcludedFromMatching() {
        User hospital = new User();
        hospital.setId("hosp1");
        hospital.setRole("hospital");
        hospital.setLat(28.6139);
        hospital.setLng(77.2090);

        when(userRepository.findById("hosp1")).thenReturn(Optional.of(hospital));

        BloodRequest req = createRequest("req_susp_1", "hosp1", "O+", 30);
        when(bloodRequestRepository.findById("req_susp_1")).thenReturn(Optional.of(req));

        LocalDateTime fresh = LocalDateTime.now().minusMinutes(5);

        // Donor 1: Active, not suspended
        User d1 = createDonor("d1", "Active Donor", "O+", 28.62, 77.21, fresh, true);
        d1.setSuspended(false);

        // Donor 2: Suspended donor (same location & blood group)
        User d2 = createDonor("d2", "Suspended Donor", "O+", 28.62, 77.21, fresh, true);
        d2.setSuspended(true);

        when(userRepository.findByRoleIgnoreCaseAndAvailableTrue("donor"))
                .thenReturn(Arrays.asList(d1, d2));

        MatchDonorsResponseDTO result = bloodRequestService.matchDonorsForRequest("req_susp_1");

        assertNotNull(result);
        assertEquals(1, result.getTotalMatched(), "Only non-suspended donor should be matched");
        assertEquals("d1", result.getDirect().get(0).getId());
    }

    @Test
    public void testSuspendedDonorExcludedFromAlerts() {
        BloodRequest br = createRequest("req_alert_susp", "hosp1", "O+", 15);
        when(bloodRequestRepository.findById("req_alert_susp")).thenReturn(Optional.of(br));

        User suspendedDonor = createDonor("d_susp", "Suspended Donor", "O+", 28.62, 77.21, LocalDateTime.now(), true);
        suspendedDonor.setSuspended(true);

        when(userRepository.findById("d_susp")).thenReturn(Optional.of(suspendedDonor));

        com.bloodlink.dto.CreateDonorAlertsRequest req = new com.bloodlink.dto.CreateDonorAlertsRequest();
        com.bloodlink.dto.CreateDonorAlertsRequest.DonorAlertItem item = new com.bloodlink.dto.CreateDonorAlertsRequest.DonorAlertItem();
        item.setDonorId("d_susp");
        item.setAlertType("direct");
        req.setDonors(Collections.singletonList(item));

        List<RequestDonor> created = bloodRequestService.createDonorAlerts("req_alert_susp", req);

        assertTrue(created.isEmpty(), "No alert should be created for a suspended donor");
        assertNull(suspendedDonor.getLastNotifiedAt(), "lastNotifiedAt should not be updated for suspended donor");
        Mockito.verify(requestDonorRepository, Mockito.never()).save(Mockito.any(RequestDonor.class));
    }

    @Test
    public void testMultipleDonorsWithSuspensionFilter() {
        User hospital = new User();
        hospital.setId("hosp1");
        hospital.setRole("hospital");
        hospital.setLat(28.6139);
        hospital.setLng(77.2090);

        when(userRepository.findById("hosp1")).thenReturn(Optional.of(hospital));

        BloodRequest req = createRequest("req_multi_susp", "hosp1", "A+", 30);
        when(bloodRequestRepository.findById("req_multi_susp")).thenReturn(Optional.of(req));

        LocalDateTime fresh = LocalDateTime.now().minusMinutes(10);

        User d1 = createDonor("d1", "Valid Donor 1", "A+", 28.62, 77.21, fresh, true);
        d1.setSuspended(false);

        User d2 = createDonor("d2", "Suspended Donor 2", "A+", 28.625, 77.21, fresh, true);
        d2.setSuspended(true);

        User d3 = createDonor("d3", "Valid Donor 3", "A+", 28.63, 77.21, fresh, true);
        d3.setSuspended(null); // null treated as not suspended

        User d4 = createDonor("d4", "Suspended Donor 4", "A+", 28.635, 77.21, fresh, true);
        d4.setSuspended(true);

        when(userRepository.findByRoleIgnoreCaseAndAvailableTrue("donor"))
                .thenReturn(Arrays.asList(d1, d2, d3, d4));

        MatchDonorsResponseDTO result = bloodRequestService.matchDonorsForRequest("req_multi_susp");

        assertNotNull(result);
        assertEquals(2, result.getTotalMatched(), "Should match exactly the 2 non-suspended donors");
        List<String> matchedIds = new ArrayList<>();
        result.getDirect().forEach(m -> matchedIds.add(m.getId()));
        result.getWaiting().forEach(m -> matchedIds.add(m.getId()));

        assertTrue(matchedIds.contains("d1"));
        assertTrue(matchedIds.contains("d3"));
        assertFalse(matchedIds.contains("d2"));
        assertFalse(matchedIds.contains("d4"));
    }

    @Test
    public void testFreshGpsUnder2Hours() {
        LocalDateTime now = LocalDateTime.now();
        User donor = createDonor("d_fresh", "Fresh Donor", "O+", 28.62, 77.21, now.minusHours(1), true);
        
        BloodRequestService.ResolvedDonorLocation res = BloodRequestService.resolveDonorLocation(donor, now);
        assertNotNull(res);
        assertEquals("fresh", res.getFreshnessStatus());
        assertEquals("GPS", res.getSource());
        assertEquals(0.0, res.getFreshnessPenalty(), 0.001);
        assertEquals(0.0, res.getSourcePenalty(), 0.001);
        assertEquals(28.62, res.getLat(), 0.001);
        assertEquals(77.21, res.getLng(), 0.001);
    }

    @Test
    public void testAgingGpsExactly2Hours() {
        LocalDateTime now = LocalDateTime.now();
        User donor = createDonor("d_aging_2h", "Aging 2h Donor", "O+", 28.62, 77.21, now.minusHours(2), true);
        
        BloodRequestService.ResolvedDonorLocation res = BloodRequestService.resolveDonorLocation(donor, now);
        assertNotNull(res);
        assertEquals("aging", res.getFreshnessStatus());
        assertEquals("GPS", res.getSource());
        assertEquals(12.0, res.getFreshnessPenalty(), 0.001);
        assertEquals(0.0, res.getSourcePenalty(), 0.001);
        assertEquals(28.62, res.getLat(), 0.001);
        assertEquals(77.21, res.getLng(), 0.001);
    }

    @Test
    public void testAgingGps12Hours() {
        LocalDateTime now = LocalDateTime.now();
        User donor = createDonor("d_aging_12h", "Aging 12h Donor", "O+", 28.62, 77.21, now.minusHours(12), true);
        
        BloodRequestService.ResolvedDonorLocation res = BloodRequestService.resolveDonorLocation(donor, now);
        assertNotNull(res);
        assertEquals("aging", res.getFreshnessStatus());
        assertEquals("GPS", res.getSource());
        assertEquals(12.0, res.getFreshnessPenalty(), 0.001);
        assertEquals(0.0, res.getSourcePenalty(), 0.001);
    }

    @Test
    public void testStaleGpsExactly24Hours() {
        LocalDateTime now = LocalDateTime.now();
        User donor = createDonor("d_stale_24h", "Stale 24h Donor", "O+", 28.62, 77.21, now.minusHours(24), true);
        
        BloodRequestService.ResolvedDonorLocation res = BloodRequestService.resolveDonorLocation(donor, now);
        assertNotNull(res);
        assertEquals("stale", res.getFreshnessStatus());
        assertEquals("GPS", res.getSource());
        assertEquals(28.0, res.getFreshnessPenalty(), 0.001);
        assertEquals(0.0, res.getSourcePenalty(), 0.001);
    }

    @Test
    public void testStaleGps48Hours() {
        LocalDateTime now = LocalDateTime.now();
        User donor = createDonor("d_stale_48h", "Stale 48h Donor", "O+", 28.62, 77.21, now.minusHours(48), true);
        
        BloodRequestService.ResolvedDonorLocation res = BloodRequestService.resolveDonorLocation(donor, now);
        assertNotNull(res);
        assertEquals("stale", res.getFreshnessStatus());
        assertEquals("GPS", res.getSource());
        assertEquals(28.0, res.getFreshnessPenalty(), 0.001);
        assertEquals(0.0, res.getSourcePenalty(), 0.001);
    }

    @Test
    public void testStaleGpsExactly72Hours() {
        LocalDateTime now = LocalDateTime.now();
        User donor = createDonor("d_stale_72h", "Stale 72h Donor", "O+", 28.62, 77.21, now.minusHours(72), true);
        
        BloodRequestService.ResolvedDonorLocation res = BloodRequestService.resolveDonorLocation(donor, now);
        assertNotNull(res);
        assertEquals("stale", res.getFreshnessStatus());
        assertEquals("GPS", res.getSource());
        assertEquals(28.0, res.getFreshnessPenalty(), 0.001);
        assertEquals(0.0, res.getSourcePenalty(), 0.001);
    }

    @Test
    public void testMoreThan72HoursFallsBackToProfileLocation() {
        LocalDateTime now = LocalDateTime.now();
        // GPS location: 28.00, 77.00 (old GPS >72h ago)
        User donor = createDonor("d_old_gps", "Old GPS Donor", "O+", 28.00, 77.00, now.minusHours(73), true);
        // Profile location: 28.62, 77.21
        donor.setRegisteredLat(28.62);
        donor.setRegisteredLng(77.21);

        BloodRequestService.ResolvedDonorLocation res = BloodRequestService.resolveDonorLocation(donor, now);
        assertNotNull(res);
        assertEquals("profile", res.getFreshnessStatus());
        assertEquals("PROFILE", res.getSource());
        assertEquals(0.0, res.getFreshnessPenalty(), 0.001);
        assertEquals(15.0, res.getSourcePenalty(), 0.001);
        // Must use profile coordinates, NOT discarded GPS coordinates
        assertEquals(28.62, res.getLat(), 0.001);
        assertEquals(77.21, res.getLng(), 0.001);
    }

    @Test
    public void testVeryOldGpsWithoutProfileLocationExcluded() {
        LocalDateTime now = LocalDateTime.now();
        User donor = createDonor("d_old_no_prof", "No Profile Donor", "O+", 28.00, 77.00, now.minusHours(80), true);
        donor.setRegisteredLat(null);
        donor.setRegisteredLng(null);

        BloodRequestService.ResolvedDonorLocation res = BloodRequestService.resolveDonorLocation(donor, now);
        assertNull(res, "Donor with >72h GPS and no profile coordinates should resolve to null and be excluded");
    }

    @Test
    public void testNullGpsTimestampFallsBackToProfileLocation() {
        LocalDateTime now = LocalDateTime.now();
        User donor = createDonor("d_null_ts", "Null TS Donor", "O+", 28.00, 77.00, null, true);
        donor.setRegisteredLat(28.62);
        donor.setRegisteredLng(77.21);

        BloodRequestService.ResolvedDonorLocation res = BloodRequestService.resolveDonorLocation(donor, now);
        assertNotNull(res);
        assertEquals("profile", res.getFreshnessStatus());
        assertEquals("PROFILE", res.getSource());
        assertEquals(0.0, res.getFreshnessPenalty(), 0.001);
        assertEquals(15.0, res.getSourcePenalty(), 0.001);
        assertEquals(28.62, res.getLat(), 0.001);
        assertEquals(77.21, res.getLng(), 0.001);
    }

    @Test
    public void testActiveCooldownAndProfileFallbackBothPenaltiesApplied() {
        User donor = createDonor("d_cd_prof", "Cooldown Profile", "O+", 28.00, 77.00, LocalDateTime.now().minusHours(80), true);
        donor.setRegisteredLat(28.62);
        donor.setRegisteredLng(77.21);
        donor.setLastNotifiedAt(LocalDateTime.now().minusMinutes(30));

        BloodRequestService.ResolvedDonorLocation res = BloodRequestService.resolveDonorLocation(donor);
        assertNotNull(res);
        assertEquals("PROFILE", res.getSource());
        assertEquals(15.0, res.getSourcePenalty(), 0.001);

        // Distance = 10.0km -> Score = 10 * 1.5 + 0 freshness + 15 source + 15 cooldown = 45.0
        double score = BloodRequestService.calculateDonorRankScore(10.0, res.getFreshnessPenalty(), res.getSourcePenalty(), donor);
        assertEquals(45.0, score, 0.001);
    }

    @Test
    public void testActiveCooldownAndFreshGpsSourceZeroCooldownFifteen() {
        User donor = createDonor("d_cd_fresh", "Cooldown Fresh", "O+", 28.62, 77.21, LocalDateTime.now().minusMinutes(10), true);
        donor.setLastNotifiedAt(LocalDateTime.now().minusMinutes(30));

        BloodRequestService.ResolvedDonorLocation res = BloodRequestService.resolveDonorLocation(donor);
        assertNotNull(res);
        assertEquals("GPS", res.getSource());
        assertEquals(0.0, res.getSourcePenalty(), 0.001);

        // Distance = 10.0km -> Score = 10 * 1.5 + 0 freshness + 0 source + 15 cooldown = 30.0
        double score = BloodRequestService.calculateDonorRankScore(10.0, res.getFreshnessPenalty(), res.getSourcePenalty(), donor);
        assertEquals(30.0, score, 0.001);
    }

    @Test
    public void testFullRankingFormulaWithSourcePenalty() {
        User hospital = new User();
        hospital.setId("hosp1");
        hospital.setRole("hospital");
        hospital.setLat(28.6139);
        hospital.setLng(77.2090);

        when(userRepository.findById("hosp1")).thenReturn(Optional.of(hospital));

        BloodRequest req = createRequest("req_rank_full", "hosp1", "O+", 30);
        when(bloodRequestRepository.findById("req_rank_full")).thenReturn(Optional.of(req));

        LocalDateTime now = LocalDateTime.now();

        // 6 donors all equidistant (~3.3km away):
        // dA: Fresh GPS (1h) -> rankScore = 3.3 * 1.5 + 0 + 0 + 0 = 4.95
        User dA = createDonor("dA", "Donor Fresh", "O+", 28.6439, 77.2090, now.minusHours(1), true);

        // dB: Aging GPS (10h) -> rankScore = 3.3 * 1.5 + 12 + 0 + 0 = 16.95
        User dB = createDonor("dB", "Donor Aging", "O+", 28.6439, 77.2090, now.minusHours(10), true);

        // dC: Profile Fallback (>72h) -> rankScore = 3.3 * 1.5 + 0 + 15 + 0 = 19.95
        User dC = createDonor("dC", "Donor Profile", "O+", 35.0, 75.0, now.minusHours(80), true);
        dC.setRegisteredLat(28.6439);
        dC.setRegisteredLng(77.2090);

        // dD: Fresh GPS + Cooldown -> rankScore = 3.3 * 1.5 + 0 + 0 + 15 = 19.95
        User dD = createDonor("dD", "Donor Fresh Cooldown", "O+", 28.6439, 77.2090, now.minusMinutes(30), true);
        dD.setLastNotifiedAt(now.minusMinutes(30));

        // dE: Stale GPS (40h) -> rankScore = 3.3 * 1.5 + 28 + 0 + 0 = 32.95
        User dE = createDonor("dE", "Donor Stale", "O+", 28.6439, 77.2090, now.minusHours(40), true);

        // dF: Profile Fallback + Cooldown -> rankScore = 3.3 * 1.5 + 0 + 15 + 15 = 34.95
        User dF = createDonor("dF", "Donor Profile Cooldown", "O+", 35.0, 75.0, now.minusHours(80), true);
        dF.setRegisteredLat(28.6439);
        dF.setRegisteredLng(77.2090);
        dF.setLastNotifiedAt(now.minusMinutes(30));

        when(userRepository.findByRoleIgnoreCaseAndAvailableTrue("donor"))
                .thenReturn(Arrays.asList(dF, dE, dC, dB, dD, dA));

        MatchDonorsResponseDTO result = bloodRequestService.matchDonorsForRequest("req_rank_full");

        assertNotNull(result);
        assertEquals(6, result.getTotalMatched());
        // Rank order: dA (4.95) < dB (16.95) < (dC/dD at 19.95) < dE (32.95) < dF (34.95)
        assertEquals("dA", result.getDirect().get(0).getId());
        assertEquals("dB", result.getDirect().get(1).getId());
        assertEquals("dE", result.getWaiting().get(1).getId());
        assertEquals("dF", result.getWaiting().get(2).getId());
    }

    @Test
    public void testRankingWithFreshnessTiersAndCooldownRegression() {
        User hospital = new User();
        hospital.setId("hosp1");
        hospital.setRole("hospital");
        hospital.setLat(28.6139);
        hospital.setLng(77.2090);

        when(userRepository.findById("hosp1")).thenReturn(Optional.of(hospital));

        BloodRequest req = createRequest("req_rank_tiers", "hosp1", "O+", 30);
        when(bloodRequestRepository.findById("req_rank_tiers")).thenReturn(Optional.of(req));

        LocalDateTime now = LocalDateTime.now();

        // Donor A: ~3.3km away, Fresh GPS (1h) -> rankScore = 3.3 * 1.5 + 0 + 0 + 0 = 4.95
        User dA = createDonor("dA", "Donor Fresh", "O+", 28.6439, 77.2090, now.minusHours(1), true);

        // Donor B: ~3.3km away, Aging GPS (10h) -> rankScore = 3.3 * 1.5 + 12 + 0 + 0 = 16.95
        User dB = createDonor("dB", "Donor Aging", "O+", 28.6439, 77.2090, now.minusHours(10), true);

        // Donor C: ~3.3km away, Stale GPS (40h) -> rankScore = 3.3 * 1.5 + 28 + 0 + 0 = 32.95
        User dC = createDonor("dC", "Donor Stale", "O+", 28.6439, 77.2090, now.minusHours(40), true);

        // Donor D: ~3.3km away, Fresh GPS (30m) but notified 30m ago (<2h) -> rankScore = 3.3 * 1.5 + 0 + 0 + 15 = 19.95
        User dD = createDonor("dD", "Donor Cooldown", "O+", 28.6439, 77.2090, now.minusMinutes(30), true);
        dD.setLastNotifiedAt(now.minusMinutes(30));

        when(userRepository.findByRoleIgnoreCaseAndAvailableTrue("donor"))
                .thenReturn(Arrays.asList(dC, dB, dD, dA));

        MatchDonorsResponseDTO result = bloodRequestService.matchDonorsForRequest("req_rank_tiers");

        assertNotNull(result);
        assertEquals(4, result.getTotalMatched());
        // Expected order by rankScore: dA (4.95) < dB (16.95) < dD (19.95) < dC (32.95)
        assertEquals("dA", result.getDirect().get(0).getId());
        assertEquals("dB", result.getDirect().get(1).getId());
        assertEquals("dD", result.getDirect().get(2).getId());
        assertEquals("dC", result.getWaiting().get(0).getId());
    }

    @Test
    public void testMatchingDiscardsOldGpsAndUsesProfileLocation() {
        User hospital = new User();
        hospital.setId("hosp1");
        hospital.setRole("hospital");
        hospital.setLat(28.6139);
        hospital.setLng(77.2090);

        when(userRepository.findById("hosp1")).thenReturn(Optional.of(hospital));

        BloodRequest req = createRequest("req_profile_fallback", "hosp1", "O+", 15);
        when(bloodRequestRepository.findById("req_profile_fallback")).thenReturn(Optional.of(req));

        LocalDateTime now = LocalDateTime.now();

        // Donor 1: Old GPS (80h ago) at 30.00, 77.00 (~150km away - out of radius),
        // but registered profile location is at 28.63, 77.21 (~2km away - within 15km radius)
        User d1 = createDonor("d1", "Profile Fallback Donor", "O+", 30.00, 77.00, now.minusHours(80), true);
        d1.setRegisteredLat(28.63);
        d1.setRegisteredLng(77.21);

        // Donor 2: Old GPS (80h ago) at 28.63, 77.21, but NO profile location -> must be excluded
        User d2 = createDonor("d2", "Excluded Old GPS Donor", "O+", 28.63, 77.21, now.minusHours(80), true);
        d2.setRegisteredLat(null);
        d2.setRegisteredLng(null);

        when(userRepository.findByRoleIgnoreCaseAndAvailableTrue("donor"))
                .thenReturn(Arrays.asList(d1, d2));

        MatchDonorsResponseDTO result = bloodRequestService.matchDonorsForRequest("req_profile_fallback");

        assertNotNull(result);
        assertEquals(1, result.getTotalMatched(), "Only donor 1 with usable profile location should be matched");
        assertEquals("d1", result.getDirect().get(0).getId());
        // Distance should be ~1.8km (from profile location), NOT ~154km (from old GPS)
        assertTrue(result.getDirect().get(0).getDistance() < 5.0);
    }

    @Test
    public void testLiveGpsUpdateDoesNotOverwriteRegisteredCoordinates() {
        com.bloodlink.service.DonorService donorService = new com.bloodlink.service.DonorService(
                userRepository, requestDonorRepository, requestBloodBankRepository, bloodRequestRepository
        );

        User donor = createDonor("d_gps_upd", "GPS Update Donor", "O+", 28.60, 77.20, LocalDateTime.now().minusDays(5), true);
        donor.setRegisteredLat(28.60);
        donor.setRegisteredLng(77.20);

        when(userRepository.findById("d_gps_upd")).thenReturn(Optional.of(donor));
        when(userRepository.save(Mockito.any(User.class))).thenAnswer(i -> i.getArgument(0));

        com.bloodlink.dto.LocationUpdateRequest updReq = new com.bloodlink.dto.LocationUpdateRequest();
        updReq.setLat(16.50);
        updReq.setLng(80.64);

        donorService.updateLocation("d_gps_upd", updReq);

        // Live GPS is updated
        assertEquals(16.50, donor.getLat(), 0.001);
        assertEquals(80.64, donor.getLng(), 0.001);
        assertNotNull(donor.getLocationUpdatedAt());

        // Registered coordinates remain strictly untouched
        assertEquals(28.60, donor.getRegisteredLat(), 0.001);
        assertEquals(77.20, donor.getRegisteredLng(), 0.001);
    }

    @Test
    public void testCalculateWaveSize() {
        // Required 1, secured 0 -> remaining 1 -> wave size 5 (min 5)
        assertEquals(5, BloodRequestService.calculateWaveSize(1));
        // Required 2, secured 0 -> remaining 2 -> wave size 5 (min 5)
        assertEquals(5, BloodRequestService.calculateWaveSize(2));
        // Required 3, secured 0 -> remaining 3 -> wave size 6
        assertEquals(6, BloodRequestService.calculateWaveSize(3));
        // Required 4, secured 0 -> remaining 4 -> wave size 8
        assertEquals(8, BloodRequestService.calculateWaveSize(4));
        // Required 5, secured 0 -> remaining 5 -> wave size 10
        assertEquals(10, BloodRequestService.calculateWaveSize(5));
        // Required 10, secured 0 -> remaining 10 -> wave size 20 (NO 15 cap)
        assertEquals(20, BloodRequestService.calculateWaveSize(10));
        // Required 20, secured 0 -> remaining 20 -> wave size 40 (NO 15 cap)
        assertEquals(40, BloodRequestService.calculateWaveSize(20));
        // Required 20, secured 10 -> remaining 10 -> wave size 20
        assertEquals(20, BloodRequestService.calculateWaveSize(20 - 10));
        // Required 20, secured 17 -> remaining 3 -> wave size 6
        assertEquals(6, BloodRequestService.calculateWaveSize(20 - 17));
        // Required 20, secured 19 -> remaining 1 -> wave size 5
        assertEquals(5, BloodRequestService.calculateWaveSize(20 - 19));
    }

    @Test
    public void testGetResponseWindowMinutes() {
        assertEquals(5, BloodRequestService.getResponseWindowMinutes("critical"));
        assertEquals(5, BloodRequestService.getResponseWindowMinutes("CRITICAL"));
        assertEquals(5, BloodRequestService.getResponseWindowMinutes("urgent"));
        assertEquals(5, BloodRequestService.getResponseWindowMinutes("URGENT"));
        assertEquals(15, BloodRequestService.getResponseWindowMinutes("Normal"));
        assertEquals(15, BloodRequestService.getResponseWindowMinutes("standard"));
        assertEquals(15, BloodRequestService.getResponseWindowMinutes(null));
    }

    @Test
    public void testCreateDonorAlertsWave1InitializationAndLimiting() {
        BloodRequest req = createRequest("req_wave_1", "hosp1", "O+", 15);
        req.setUnits(1);
        req.setUrgency("Urgent");
        when(bloodRequestRepository.findById("req_wave_1")).thenReturn(Optional.of(req));
        when(bloodRequestRepository.save(Mockito.any(BloodRequest.class))).thenAnswer(i -> i.getArgument(0));

        List<CreateDonorAlertsRequest.DonorAlertItem> items = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        for (int i = 1; i <= 8; i++) {
            String donorId = "donor_" + i;
            User d = createDonor(donorId, "Donor " + i, "O+", 28.60, 77.20, now, true);
            when(userRepository.findById(donorId)).thenReturn(Optional.of(d));
            when(userRepository.save(Mockito.any(User.class))).thenAnswer(inv -> inv.getArgument(0));
            when(requestDonorRepository.findByDonorIdAndBloodRequestId(donorId, "req_wave_1")).thenReturn(Optional.empty());

            CreateDonorAlertsRequest.DonorAlertItem item = new CreateDonorAlertsRequest.DonorAlertItem();
            item.setDonorId(donorId);
            item.setAlertType("direct");
            items.add(item);
        }

        when(requestDonorRepository.save(Mockito.any(RequestDonor.class))).thenAnswer(i -> i.getArgument(0));

        CreateDonorAlertsRequest alertReq = new CreateDonorAlertsRequest();
        alertReq.setDonors(items);

        List<RequestDonor> result = bloodRequestService.createDonorAlerts("req_wave_1", alertReq);

        // 1 unit -> waveSize = 5. Even though 8 donors were submitted, only 5 are alerted in Wave 1
        assertEquals(5, result.size());
        assertEquals(1, req.getCurrentWave());
        assertNotNull(req.getWaveStartedAt());
        assertNotNull(req.getWaveExpiresAt());
        // Urgent -> 5 minutes expiration
        assertEquals(req.getWaveStartedAt().plusMinutes(5).getMinute(), req.getWaveExpiresAt().getMinute());
        assertTrue(req.getDonorAlertsSent());
    }

    @Test
    public void testProgressDonorAlertWavesWhenWindowExpires() {
        User hospital = new User();
        hospital.setId("hosp1");
        hospital.setRole("hospital");
        hospital.setLat(28.6139);
        hospital.setLng(77.2090);
        when(userRepository.findById("hosp1")).thenReturn(Optional.of(hospital));

        BloodRequest req = createRequest("req_wave_exp", "hosp1", "O+", 15);
        req.setUnits(2); // remaining = 2 -> next wave size = 5
        req.setSecuredUnits(0);
        req.setUrgency("Critical");
        req.setDonorAlertsSent(true);
        req.setStatus("open");
        req.setCurrentWave(1);
        LocalDateTime now = LocalDateTime.now();
        req.setWaveStartedAt(now.minusMinutes(10));
        req.setWaveExpiresAt(now.minusMinutes(5)); // Wave 1 expired 5 minutes ago

        when(bloodRequestRepository.findById("req_wave_exp")).thenReturn(Optional.of(req));
        when(bloodRequestRepository.save(Mockito.any(BloodRequest.class))).thenAnswer(i -> i.getArgument(0));

        // Existing Wave 1 had donors d1, d2, d3
        List<RequestDonor> existingDonors = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            RequestDonor rd = new RequestDonor();
            rd.setBloodRequest(req);
            rd.setDonorId("d" + i);
            rd.setStatus("pending");
            existingDonors.add(rd);
        }
        when(requestDonorRepository.findByBloodRequestId("req_wave_exp")).thenReturn(existingDonors);

        // All available donors in database: d1, d2, d3 (already alerted) and d4, d5, d6, d7, d8, d9 (new)
        List<User> availableDonors = new ArrayList<>();
        for (int i = 1; i <= 9; i++) {
            User d = createDonor("d" + i, "Donor " + i, "O+", 28.62, 77.21, now, true);
            availableDonors.add(d);
            when(userRepository.findById("d" + i)).thenReturn(Optional.of(d));
            when(userRepository.save(Mockito.any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        }
        when(userRepository.findByRoleIgnoreCaseAndAvailableTrue("donor")).thenReturn(availableDonors);
        when(requestDonorRepository.save(Mockito.any(RequestDonor.class))).thenAnswer(i -> i.getArgument(0));

        List<RequestDonor> nextWaveAlerts = bloodRequestService.progressDonorAlertWavesForRequest("req_wave_exp");

        // Should alert next wave of 5 donors (d4, d5, d6, d7, d8), excluding d1, d2, d3
        assertEquals(5, nextWaveAlerts.size());
        for (RequestDonor rd : nextWaveAlerts) {
            assertNotEquals("d1", rd.getDonorId());
            assertNotEquals("d2", rd.getDonorId());
            assertNotEquals("d3", rd.getDonorId());
        }

        assertEquals(2, req.getCurrentWave());
        assertNotNull(req.getWaveStartedAt());
        assertNotNull(req.getWaveExpiresAt());
    }

    @Test
    public void testProgressDonorAlertWavesStopsWhenSecuredUnitsMet() {
        BloodRequest req = createRequest("req_wave_secured", "hosp1", "O+", 15);
        req.setUnits(2);
        req.setSecuredUnits(2); // Fully secured
        req.setDonorAlertsSent(true);
        req.setStatus("open");
        req.setCurrentWave(1);
        LocalDateTime now = LocalDateTime.now();
        req.setWaveExpiresAt(now.minusMinutes(5));

        when(bloodRequestRepository.findById("req_wave_secured")).thenReturn(Optional.of(req));

        List<RequestDonor> alerts = bloodRequestService.progressDonorAlertWavesForRequest("req_wave_secured");
        assertTrue(alerts.isEmpty());
        assertEquals(1, req.getCurrentWave());
    }

    @Test
    public void testProgressDonorAlertWavesNoopBeforeExpiration() {
        BloodRequest req = createRequest("req_wave_active", "hosp1", "O+", 15);
        req.setUnits(2);
        req.setSecuredUnits(0);
        req.setDonorAlertsSent(true);
        req.setStatus("open");
        req.setCurrentWave(1);
        LocalDateTime now = LocalDateTime.now();
        req.setWaveStartedAt(now.minusMinutes(1));
        req.setWaveExpiresAt(now.plusMinutes(4)); // Not expired yet

        when(bloodRequestRepository.findById("req_wave_active")).thenReturn(Optional.of(req));

        List<RequestDonor> alerts = bloodRequestService.progressDonorAlertWavesForRequest("req_wave_active");
        assertTrue(alerts.isEmpty());
        assertEquals(1, req.getCurrentWave());
    }

    @Test
    public void testUncappedWaveSizeProgressionWithPartialFulfillment() {
        User hospital = new User();
        hospital.setId("hosp1");
        hospital.setRole("hospital");
        hospital.setLat(28.6139);
        hospital.setLng(77.2090);
        when(userRepository.findById("hosp1")).thenReturn(Optional.of(hospital));

        LocalDateTime now = LocalDateTime.now();

        // 1. Required = 10, Secured = 0 -> Wave 1 alerts 20 donors (NO 15 cap)
        BloodRequest req10 = createRequest("req_10_units", "hosp1", "O+", 30);
        req10.setUnits(10);
        req10.setUrgency("Urgent");
        when(bloodRequestRepository.findById("req_10_units")).thenReturn(Optional.of(req10));
        when(bloodRequestRepository.save(Mockito.any(BloodRequest.class))).thenAnswer(i -> i.getArgument(0));

        List<CreateDonorAlertsRequest.DonorAlertItem> items25 = new ArrayList<>();
        for (int i = 1; i <= 25; i++) {
            String dId = "donor_uncapped_" + i;
            User d = createDonor(dId, "Donor " + i, "O+", 28.60, 77.20, now, true);
            when(userRepository.findById(dId)).thenReturn(Optional.of(d));
            when(userRepository.save(Mockito.any(User.class))).thenAnswer(inv -> inv.getArgument(0));
            when(requestDonorRepository.findByDonorIdAndBloodRequestId(dId, "req_10_units")).thenReturn(Optional.empty());

            CreateDonorAlertsRequest.DonorAlertItem item = new CreateDonorAlertsRequest.DonorAlertItem();
            item.setDonorId(dId);
            item.setAlertType("direct");
            items25.add(item);
        }

        when(requestDonorRepository.save(Mockito.any(RequestDonor.class))).thenAnswer(i -> i.getArgument(0));

        CreateDonorAlertsRequest alertReq = new CreateDonorAlertsRequest();
        alertReq.setDonors(items25);

        List<RequestDonor> wave1Alerts = bloodRequestService.createDonorAlerts("req_10_units", alertReq);
        // Required 10 -> Wave size = max(5, 10 * 2) = 20. Exactly 20 alerts created (exceeds old 15 cap)
        assertEquals(20, wave1Alerts.size());

        // 2. Required = 20, Secured = 10 -> Remaining = 10 -> Next wave size = 20
        BloodRequest req20Sec10 = createRequest("req_20_sec_10", "hosp1", "O+", 30);
        req20Sec10.setUnits(20);
        req20Sec10.setSecuredUnits(10);
        req20Sec10.setDonorAlertsSent(true);
        req20Sec10.setStatus("open");
        req20Sec10.setCurrentWave(1);
        req20Sec10.setWaveStartedAt(now.minusMinutes(10));
        req20Sec10.setWaveExpiresAt(now.minusMinutes(5));

        when(bloodRequestRepository.findById("req_20_sec_10")).thenReturn(Optional.of(req20Sec10));

        // Mock 25 available un-alerted donors
        List<User> donorPool = new ArrayList<>();
        for (int i = 1; i <= 25; i++) {
            User d = createDonor("d_pool_" + i, "Donor " + i, "O+", 28.62, 77.21, now, true);
            donorPool.add(d);
            when(userRepository.findById("d_pool_" + i)).thenReturn(Optional.of(d));
            when(userRepository.save(Mockito.any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        }
        when(userRepository.findByRoleIgnoreCaseAndAvailableTrue("donor")).thenReturn(donorPool);
        when(requestDonorRepository.findByBloodRequestId("req_20_sec_10")).thenReturn(Collections.emptyList());

        List<RequestDonor> nextWave20 = bloodRequestService.progressDonorAlertWavesForRequest("req_20_sec_10");
        // Remaining 10 -> wave size = 20
        assertEquals(20, nextWave20.size());

        // 3. Required = 20, Secured = 17 -> Remaining = 3 -> Next wave size = 6
        BloodRequest req20Sec17 = createRequest("req_20_sec_17", "hosp1", "O+", 30);
        req20Sec17.setUnits(20);
        req20Sec17.setSecuredUnits(17);
        req20Sec17.setDonorAlertsSent(true);
        req20Sec17.setStatus("open");
        req20Sec17.setCurrentWave(2);
        req20Sec17.setWaveStartedAt(now.minusMinutes(10));
        req20Sec17.setWaveExpiresAt(now.minusMinutes(5));

        when(bloodRequestRepository.findById("req_20_sec_17")).thenReturn(Optional.of(req20Sec17));
        when(requestDonorRepository.findByBloodRequestId("req_20_sec_17")).thenReturn(Collections.emptyList());

        List<RequestDonor> nextWave6 = bloodRequestService.progressDonorAlertWavesForRequest("req_20_sec_17");
        // Remaining 3 -> wave size = 6
        assertEquals(6, nextWave6.size());

        // 4. Required = 20, Secured = 19 -> Remaining = 1 -> Next wave size = 5
        BloodRequest req20Sec19 = createRequest("req_20_sec_19", "hosp1", "O+", 30);
        req20Sec19.setUnits(20);
        req20Sec19.setSecuredUnits(19);
        req20Sec19.setDonorAlertsSent(true);
        req20Sec19.setStatus("open");
        req20Sec19.setCurrentWave(3);
        req20Sec19.setWaveStartedAt(now.minusMinutes(10));
        req20Sec19.setWaveExpiresAt(now.minusMinutes(5));

        when(bloodRequestRepository.findById("req_20_sec_19")).thenReturn(Optional.of(req20Sec19));
        when(requestDonorRepository.findByBloodRequestId("req_20_sec_19")).thenReturn(Collections.emptyList());

        List<RequestDonor> nextWave5 = bloodRequestService.progressDonorAlertWavesForRequest("req_20_sec_19");
        // Remaining 1 -> wave size = 5
        assertEquals(5, nextWave5.size());

        // 5. Required = 20, Secured = 20 -> No next wave
        BloodRequest req20Sec20 = createRequest("req_20_sec_20", "hosp1", "O+", 30);
        req20Sec20.setUnits(20);
        req20Sec20.setSecuredUnits(20);
        req20Sec20.setDonorAlertsSent(true);
        req20Sec20.setStatus("open");
        req20Sec20.setCurrentWave(4);
        req20Sec20.setWaveStartedAt(now.minusMinutes(10));
        req20Sec20.setWaveExpiresAt(now.minusMinutes(5));

        when(bloodRequestRepository.findById("req_20_sec_20")).thenReturn(Optional.of(req20Sec20));

        List<RequestDonor> nextWave0 = bloodRequestService.progressDonorAlertWavesForRequest("req_20_sec_20");
        assertTrue(nextWave0.isEmpty());
        assertEquals(4, req20Sec20.getCurrentWave());
    }

    @Test
    public void testRequestNotFoundThrowsException() {
        when(bloodRequestRepository.findById("non_existent")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> {
            bloodRequestService.matchDonorsForRequest("non_existent");
        });
    }
}
