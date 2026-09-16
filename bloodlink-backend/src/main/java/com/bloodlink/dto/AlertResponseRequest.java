package com.bloodlink.dto;

public class AlertResponseRequest {
    private String status; // "accepted" | "rejected" | "declined"
    private Double lat;
    private Double lng;

    public AlertResponseRequest() {}

    public AlertResponseRequest(String status) {
        this.status = status;
    }

    public AlertResponseRequest(String status, Double lat, Double lng) {
        this.status = status;
        this.lat = lat;
        this.lng = lng;
    }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Double getLat() { return lat; }
    public void setLat(Double lat) { this.lat = lat; }

    public Double getLng() { return lng; }
    public void setLng(Double lng) { this.lng = lng; }
}
