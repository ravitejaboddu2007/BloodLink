package com.bloodlink.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class DonorAlertDTO {
    private String id;
    private String hospitalId;
    private String hospitalName;
    private String hospitalCity;
    private String hospitalPhone;
    private String patientName;
    private String bloodGroup;
    private Integer units;
    private String urgency;
    private String contact;
    private String notes;
    private String status;
    private Integer radius;

    @JsonProperty("dType")
    private String dType;

    @JsonProperty("dStatus")
    private String dStatus;

    private String respondedAt;
    private String createdAt;
    private String requestType; // "EMERGENCY" or "SCHEDULED"
    private String reason;
    private String operationTime;
    private String accumulationDeadline;

    public DonorAlertDTO() {}

    public String getRequestType() { return requestType; }
    public void setRequestType(String requestType) { this.requestType = requestType; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getOperationTime() { return operationTime; }
    public void setOperationTime(String operationTime) { this.operationTime = operationTime; }

    public String getAccumulationDeadline() { return accumulationDeadline; }
    public void setAccumulationDeadline(String accumulationDeadline) { this.accumulationDeadline = accumulationDeadline; }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getHospitalId() { return hospitalId; }
    public void setHospitalId(String hospitalId) { this.hospitalId = hospitalId; }

    public String getHospitalName() { return hospitalName; }
    public void setHospitalName(String hospitalName) { this.hospitalName = hospitalName; }

    public String getHospitalCity() { return hospitalCity; }
    public void setHospitalCity(String hospitalCity) { this.hospitalCity = hospitalCity; }

    public String getHospitalPhone() { return hospitalPhone; }
    public void setHospitalPhone(String hospitalPhone) { this.hospitalPhone = hospitalPhone; }

    public String getPatientName() { return patientName; }
    public void setPatientName(String patientName) { this.patientName = patientName; }

    public String getBloodGroup() { return bloodGroup; }
    public void setBloodGroup(String bloodGroup) { this.bloodGroup = bloodGroup; }

    public Integer getUnits() { return units; }
    public void setUnits(Integer units) { this.units = units; }

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

    @JsonProperty("dType")
    public String getDType() { return dType; }
    public void setDType(String dType) { this.dType = dType; }

    @JsonProperty("dStatus")
    public String getDStatus() { return dStatus; }
    public void setDStatus(String dStatus) { this.dStatus = dStatus; }

    public String getRespondedAt() { return respondedAt; }
    public void setRespondedAt(String respondedAt) { this.respondedAt = respondedAt; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
