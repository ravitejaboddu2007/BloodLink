package com.bloodlink.dto;

public class DonationHistoryDTO {
    private String id;
    private String donorId;
    private String date;
    private String location;
    private Integer units;
    private String notes;
    private String createdAt;

    public DonationHistoryDTO() {}

    public DonationHistoryDTO(String id, String donorId, String date, String location, Integer units, String notes, String createdAt) {
        this.id = id;
        this.donorId = donorId;
        this.date = date;
        this.location = location;
        this.units = units;
        this.notes = notes;
        this.createdAt = createdAt;
    }

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

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}