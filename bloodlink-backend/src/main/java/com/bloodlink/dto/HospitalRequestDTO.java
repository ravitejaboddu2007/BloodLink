package com.bloodlink.dto;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HospitalRequestDTO {

    private String id;
    private String hospitalId;
    private String patientName;
    private String bloodGroup;
    private Integer units;
    private Integer securedUnits = 0;
    private String urgency;
    private String contact;
    private String notes;
    private String status;
    private Integer radius;
    private Boolean donorAlertsSent = false;
    private Integer remainingUnitsAtDonorAlert;
    private Integer currentWave = 1;
    private String waveStartedAt;
    private String waveExpiresAt;
    private String createdAt;
    private String fulfilledAt;
    private String requestType; // "EMERGENCY" or "SCHEDULED"
    private String reason;
    private String operationTime;
    private String accumulationDeadline;
    private Boolean isExpired = false;

    private List<Map<String, Object>> bloodBanks = new ArrayList<>();
    private List<Map<String, Object>> directDonors = new ArrayList<>();
    private List<Map<String, Object>> waitingDonors = new ArrayList<>();

    public HospitalRequestDTO() {}

    public String getRequestType() { return requestType; }
    public void setRequestType(String requestType) { this.requestType = requestType; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getOperationTime() { return operationTime; }
    public void setOperationTime(String operationTime) { this.operationTime = operationTime; }

    public String getAccumulationDeadline() { return accumulationDeadline; }
    public void setAccumulationDeadline(String accumulationDeadline) { this.accumulationDeadline = accumulationDeadline; }

    public Boolean getIsExpired() { return isExpired; }
    public void setIsExpired(Boolean isExpired) { this.isExpired = isExpired; }

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

    public String getWaveStartedAt() { return waveStartedAt; }
    public void setWaveStartedAt(String waveStartedAt) { this.waveStartedAt = waveStartedAt; }

    public String getWaveExpiresAt() { return waveExpiresAt; }
    public void setWaveExpiresAt(String waveExpiresAt) { this.waveExpiresAt = waveExpiresAt; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getFulfilledAt() { return fulfilledAt; }
    public void setFulfilledAt(String fulfilledAt) { this.fulfilledAt = fulfilledAt; }

    public List<Map<String, Object>> getBloodBanks() { return bloodBanks; }
    public void setBloodBanks(List<Map<String, Object>> bloodBanks) { this.bloodBanks = bloodBanks; }

    public List<Map<String, Object>> getDirectDonors() { return directDonors; }
    public void setDirectDonors(List<Map<String, Object>> directDonors) { this.directDonors = directDonors; }

    public List<Map<String, Object>> getWaitingDonors() { return waitingDonors; }
    public void setWaitingDonors(List<Map<String, Object>> waitingDonors) { this.waitingDonors = waitingDonors; }
}
