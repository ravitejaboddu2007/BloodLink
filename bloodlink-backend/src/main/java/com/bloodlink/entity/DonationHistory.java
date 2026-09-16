package com.bloodlink.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "donation_history")
public class DonationHistory {

    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "donor_id", nullable = false, length = 64)
    private String donorId;

    @Column(nullable = false, length = 30)
    private String date;

    @Column(nullable = false, length = 200)
    private String location;

    @Column(nullable = false)
    private Integer units;

    @Column(length = 500)
    private String notes;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public DonationHistory() {}

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getDonorId() { return donorId; }
    public void setDonorId(String donorId) { this.donorId = donorId; }

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public Integer getUnits() { return units; }
    public void setUnits(Integer units) { this.units = units; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
