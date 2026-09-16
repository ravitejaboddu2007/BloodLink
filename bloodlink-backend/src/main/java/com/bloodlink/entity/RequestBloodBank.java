package com.bloodlink.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "request_blood_banks")
public class RequestBloodBank {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "request_id", nullable = false)
    @JsonIgnore
    private BloodRequest bloodRequest;

    @Column(name = "blood_bank_id", nullable = false, length = 64)
    private String bloodBankId;

    private Double distance;

    @Column(name = "available_units_at_match")
    private Integer availableUnitsAtMatch;

    @Column(length = 30)
    private String status; // "sufficient", "partial"

    @Column(name = "response_status", length = 30)
    private String responseStatus; // "pending", "waiting", "accepted", "rejected", "closed"

    @Column(name = "units_secured")
    private Integer unitsSecured = 0;

    @Column(name = "reserved_units")
    private Integer reservedUnits = 0;

    @Column(name = "reserved_groups_json", length = 500)
    private String reservedGroupsJson;

    @Column(name = "responded_at")
    private LocalDateTime respondedAt;

    @Column(nullable = false)
    private Boolean unavailable = false;

    public RequestBloodBank() {}

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public BloodRequest getBloodRequest() { return bloodRequest; }
    public void setBloodRequest(BloodRequest bloodRequest) { this.bloodRequest = bloodRequest; }

    public String getBloodBankId() { return bloodBankId; }
    public void setBloodBankId(String bloodBankId) { this.bloodBankId = bloodBankId; }

    public Double getDistance() { return distance; }
    public void setDistance(Double distance) { this.distance = distance; }

    public Integer getAvailableUnitsAtMatch() { return availableUnitsAtMatch; }
    public void setAvailableUnitsAtMatch(Integer availableUnitsAtMatch) { this.availableUnitsAtMatch = availableUnitsAtMatch; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getResponseStatus() { return responseStatus; }
    public void setResponseStatus(String responseStatus) { this.responseStatus = responseStatus; }

    public Integer getUnitsSecured() { return unitsSecured; }
    public void setUnitsSecured(Integer unitsSecured) { this.unitsSecured = unitsSecured; }

    public Integer getReservedUnits() { return reservedUnits != null ? reservedUnits : 0; }
    public void setReservedUnits(Integer reservedUnits) { this.reservedUnits = reservedUnits != null ? reservedUnits : 0; }

    public String getReservedGroupsJson() { return reservedGroupsJson; }
    public void setReservedGroupsJson(String reservedGroupsJson) { this.reservedGroupsJson = reservedGroupsJson; }

    public LocalDateTime getRespondedAt() { return respondedAt; }
    public void setRespondedAt(LocalDateTime respondedAt) { this.respondedAt = respondedAt; }

    public Boolean getUnavailable() { return unavailable; }
    public void setUnavailable(Boolean unavailable) { this.unavailable = unavailable; }
}
