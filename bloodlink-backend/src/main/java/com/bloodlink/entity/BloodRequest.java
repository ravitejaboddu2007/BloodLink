package com.bloodlink.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "blood_requests")
public class BloodRequest {

    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "hospital_id", nullable = false, length = 64)
    private String hospitalId;

    @Column(name = "patient_name", nullable = false, length = 150)
    private String patientName;

    @Column(name = "blood_group", nullable = false, length = 10)
    private String bloodGroup;

    @Column(nullable = false)
    private Integer units;

    @Column(name = "secured_units")
    private Integer securedUnits = 0;

    @Column(nullable = false, length = 20)
    private String urgency;

    @Column(nullable = false, length = 50)
    private String contact;

    @Column(length = 500)
    private String notes;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(nullable = false)
    private Integer radius;

    @Column(name = "donor_alerts_sent")
    private Boolean donorAlertsSent = false;

    @Column(name = "remaining_units_at_donor_alert")
    private Integer remainingUnitsAtDonorAlert;

    @Column(name = "current_wave")
    private Integer currentWave = 1;

    @Column(name = "wave_started_at")
    private LocalDateTime waveStartedAt;

    @Column(name = "wave_expires_at")
    private LocalDateTime waveExpiresAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "fulfilled_at")
    private LocalDateTime fulfilledAt;

    @Column(name = "request_type", nullable = false, length = 20)
    private String requestType = "EMERGENCY"; // "EMERGENCY", "SCHEDULED"

    @Column(length = 250)
    private String reason;

    @Column(name = "operation_time")
    private LocalDateTime operationTime;

    @Column(name = "accumulation_deadline")
    private LocalDateTime accumulationDeadline;

    @OneToMany(mappedBy = "bloodRequest", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RequestBloodBank> bloodBanks = new ArrayList<>();

    @OneToMany(mappedBy = "bloodRequest", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RequestDonor> donors = new ArrayList<>();

    public BloodRequest() {}

    // Getters and Setters
    public String getRequestType() { return requestType; }
    public void setRequestType(String requestType) { this.requestType = requestType; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public LocalDateTime getOperationTime() { return operationTime; }
    public void setOperationTime(LocalDateTime operationTime) { this.operationTime = operationTime; }

    public LocalDateTime getAccumulationDeadline() { return accumulationDeadline; }
    public void setAccumulationDeadline(LocalDateTime accumulationDeadline) { this.accumulationDeadline = accumulationDeadline; }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getHospitalId() { return hospitalId; }
    public void setHospitalId(String hospitalId) { this.hospitalId = hospitalId; }

    public String getPatientName() { return patientName; }
    public void setPatientName(String patientName) { this.patientName = patientName; }

    public String getBloodGroup() { return bloodGroup; }
    public void setBloodGroup(String bloodGroup) { this.bloodGroup = bloodGroup; }

    public Integer getUnits() { return units; }
    public void setUnits(Integer units) { this.units = units; }

    public Integer getSecuredUnits() { return securedUnits; }
    public void setSecuredUnits(Integer securedUnits) { this.securedUnits = securedUnits; }

    public String getUrgency() { return urgency; }
    public void setUrgency(String urgency) { this.urgency = urgency; }

    public String getContact() { return contact; }
    public void setContact(String contact) { this.contact = contact; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Integer getRadius() { return radius; }
    public void setRadius(Integer radius) { this.radius = radius; }

    public Boolean getDonorAlertsSent() { return donorAlertsSent; }
    public void setDonorAlertsSent(Boolean donorAlertsSent) { this.donorAlertsSent = donorAlertsSent; }

    public Integer getRemainingUnitsAtDonorAlert() { return remainingUnitsAtDonorAlert; }
    public void setRemainingUnitsAtDonorAlert(Integer remainingUnitsAtDonorAlert) { this.remainingUnitsAtDonorAlert = remainingUnitsAtDonorAlert; }

    public Integer getCurrentWave() { return currentWave; }
    public void setCurrentWave(Integer currentWave) { this.currentWave = currentWave; }

    public LocalDateTime getWaveStartedAt() { return waveStartedAt; }
    public void setWaveStartedAt(LocalDateTime waveStartedAt) { this.waveStartedAt = waveStartedAt; }

    public LocalDateTime getWaveExpiresAt() { return waveExpiresAt; }
    public void setWaveExpiresAt(LocalDateTime waveExpiresAt) { this.waveExpiresAt = waveExpiresAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getFulfilledAt() { return fulfilledAt; }
    public void setFulfilledAt(LocalDateTime fulfilledAt) { this.fulfilledAt = fulfilledAt; }

    public List<RequestBloodBank> getBloodBanks() { return bloodBanks; }
    public void setBloodBanks(List<RequestBloodBank> bloodBanks) { this.bloodBanks = bloodBanks; }

    public List<RequestDonor> getDonors() { return donors; }
    public void setDonors(List<RequestDonor> donors) { this.donors = donors; }
}
