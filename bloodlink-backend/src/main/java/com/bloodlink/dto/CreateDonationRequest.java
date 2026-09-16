package com.bloodlink.dto;

public class CreateDonationRequest {
    private String date;
    private String location;
    private Integer units;
    private String notes;

    public CreateDonationRequest() {}

    public CreateDonationRequest(String date, String location, Integer units, String notes) {
        this.date = date;
        this.location = location;
        this.units = units;
        this.notes = notes;
    }

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public Integer getUnits() { return units; }
    public void setUnits(Integer units) { this.units = units; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}