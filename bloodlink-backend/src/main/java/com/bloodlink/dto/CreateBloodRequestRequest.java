package com.bloodlink.dto;

public class CreateBloodRequestRequest {
    private String hospitalId;
    private String patientName;
    private String bloodGroup;
    private Integer units;
    private String urgency;
    private String contact;
    private String notes;
    private Integer radius;
    private String requestType; // "EMERGENCY" or "SCHEDULED"
    private String reason;
    private String operationTime;
    private String accumulationDeadline;

    public CreateBloodRequestRequest() {}

    public String getHospitalId() { return hospitalId; }
    public void setHospitalId(String hospitalId) { this.hospitalId = hospitalId; }

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

    public Integer getRadius() { return radius; }
    public void setRadius(Integer radius) { this.radius = radius; }

    public String getRequestType() { return requestType; }
    public void setRequestType(String requestType) { this.requestType = requestType; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getOperationTime() { return operationTime; }
    public void setOperationTime(String operationTime) { this.operationTime = operationTime; }

    public String getAccumulationDeadline() { return accumulationDeadline; }
    public void setAccumulationDeadline(String accumulationDeadline) { this.accumulationDeadline = accumulationDeadline; }
}
