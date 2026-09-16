package com.bloodlink.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class DonorSearchItemDTO {
    private String id;
    private String name;
    private String bloodGroup;
    private String phone;
    private String city;
    private String state;
    private Integer age;
    private Boolean available;
    private String lastDonation;
    private String nextEligibleDate;
    private Boolean eligible;
    private String reportData;
    private String reportName;
    private Double lat;
    private Double lng;
    private String locationUpdatedAt;
    private Double distance;

    @JsonProperty("_d")
    private Double _d;

    public DonorSearchItemDTO() {}

    public DonorSearchItemDTO(String id, String name, String bloodGroup, String phone,
                              String city, String state, Integer age, Boolean available,
                              String lastDonation, String reportData, String reportName,
                              Double lat, Double lng, String locationUpdatedAt, Double distance) {
        this.id = id;
        this.name = name;
        this.bloodGroup = bloodGroup;
        this.phone = phone;
        this.city = city;
        this.state = state;
        this.age = age;
        this.available = available;
        this.lastDonation = lastDonation;
        this.reportData = reportData;
        this.reportName = reportName;
        this.lat = lat;
        this.lng = lng;
        this.locationUpdatedAt = locationUpdatedAt;
        this.distance = distance;
        this._d = distance;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getBloodGroup() { return bloodGroup; }
    public void setBloodGroup(String bloodGroup) { this.bloodGroup = bloodGroup; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getState() { return state; }
    public void setState(String state) { this.state = state; }

    public Integer getAge() { return age; }
    public void setAge(Integer age) { this.age = age; }

    public Boolean getAvailable() { return available; }
    public void setAvailable(Boolean available) { this.available = available; }

    public String getLastDonation() { return lastDonation; }
    public void setLastDonation(String lastDonation) { this.lastDonation = lastDonation; }

    public String getNextEligibleDate() { return nextEligibleDate; }
    public void setNextEligibleDate(String nextEligibleDate) { this.nextEligibleDate = nextEligibleDate; }

    public Boolean getEligible() { return eligible; }
    public void setEligible(Boolean eligible) { this.eligible = eligible; }

    public String getReportData() { return reportData; }
    public void setReportData(String reportData) { this.reportData = reportData; }

    public String getReportName() { return reportName; }
    public void setReportName(String reportName) { this.reportName = reportName; }

    public Double getLat() { return lat; }
    public void setLat(Double lat) { this.lat = lat; }

    public Double getLng() { return lng; }
    public void setLng(Double lng) { this.lng = lng; }

    public String getLocationUpdatedAt() { return locationUpdatedAt; }
    public void setLocationUpdatedAt(String locationUpdatedAt) { this.locationUpdatedAt = locationUpdatedAt; }

    public Double getDistance() { return distance; }
    public void setDistance(Double distance) {
        this.distance = distance;
        this._d = distance;
    }

    @JsonProperty("_d")
    public Double get_d() { return _d; }

    @JsonProperty("_d")
    public void set_d(Double _d) {
        this._d = _d;
        this.distance = _d;
    }
}