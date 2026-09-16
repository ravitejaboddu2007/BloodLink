package com.bloodlink.dto;

public class AvailabilityRequest {
    private Boolean available;

    public AvailabilityRequest() {}

    public AvailabilityRequest(Boolean available) {
        this.available = available;
    }

    public Boolean getAvailable() { return available; }
    public void setAvailable(Boolean available) { this.available = available; }
}
