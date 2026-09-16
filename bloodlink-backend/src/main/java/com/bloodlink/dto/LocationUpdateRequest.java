package com.bloodlink.dto;

public class LocationUpdateRequest {
    private Double lat;
    private Double lng;
    private String city;
    private String state;

    public LocationUpdateRequest() {}

    public LocationUpdateRequest(Double lat, Double lng, String city, String state) {
        this.lat = lat;
        this.lng = lng;
        this.city = city;
        this.state = state;
    }

    public Double getLat() { return lat; }
    public void setLat(Double lat) { this.lat = lat; }

    public Double getLng() { return lng; }
    public void setLng(Double lng) { this.lng = lng; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
}
