package com.bloodlink.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "request_donors")
public class RequestDonor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "request_id", nullable = false)
    @JsonIgnore
    private BloodRequest bloodRequest;

    @Column(name = "donor_id", nullable = false, length = 64)
    private String donorId;

    @Column(name = "alert_type", nullable = false, length = 20)
    private String alertType; // "direct", "waiting"

    @Column(nullable = false, length = 20)
    private String status; // "pending", "accepted", "rejected"

    @Column(name = "responded_at")
    private LocalDateTime respondedAt;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    public RequestDonor() {}

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public BloodRequest getBloodRequest() { return bloodRequest; }
    public void setBloodRequest(BloodRequest bloodRequest) { this.bloodRequest = bloodRequest; }

    public String getDonorId() { return donorId; }
    public void setDonorId(String donorId) { this.donorId = donorId; }

    public String getAlertType() { return alertType; }
    public void setAlertType(String alertType) { this.alertType = alertType; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getRespondedAt() { return respondedAt; }
    public void setRespondedAt(LocalDateTime respondedAt) { this.respondedAt = respondedAt; }

    public LocalDateTime getConfirmedAt() { return confirmedAt; }
    public void setConfirmedAt(LocalDateTime confirmedAt) { this.confirmedAt = confirmedAt; }
}
