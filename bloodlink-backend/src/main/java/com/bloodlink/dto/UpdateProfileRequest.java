package com.bloodlink.dto;

public class UpdateProfileRequest {
    private String name;
    private String phone;
    private Integer age;
    private String bloodGroup;
    private String city;
    private String state;
    private String address;
    private String registrationNumber;
    private String operatingHours;
    private Double lat;
    private Double lng;
    private String locationUpdatedAt;
    private Boolean available;
    private String lastDonation;
    private String reportData;
    private String reportName;

    public UpdateProfileRequest() {}

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public Integer getAge() { return age; }
    public void setAge(Integer age) { this.age = age; }

    public String getBloodGroup() { return bloodGroup; }
    public void setBloodGroup(String bloodGroup) { this.bloodGroup = bloodGroup; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getState() { return state; }
    public void setState(String state) { this.state = state; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getRegistrationNumber() { return registrationNumber; }
    public void setRegistrationNumber(String registrationNumber) { this.registrationNumber = registrationNumber; }

    public String getOperatingHours() { return operatingHours; }
    public void setOperatingHours(String operatingHours) { this.operatingHours = operatingHours; }

    public Double getLat() { return lat; }
    public void setLat(Double lat) { this.lat = lat; }

    public Double getLng() { return lng; }
    public void setLng(Double lng) { this.lng = lng; }

    public String getLocationUpdatedAt() { return locationUpdatedAt; }
    public void setLocationUpdatedAt(String locationUpdatedAt) { this.locationUpdatedAt = locationUpdatedAt; }

    public Boolean getAvailable() { return available; }
    public void setAvailable(Boolean available) { this.available = available; }

    public String getLastDonation() { return lastDonation; }
    public void setLastDonation(String lastDonation) { this.lastDonation = lastDonation; }

    public String getReportData() { return reportData; }
    public void setReportData(String reportData) { this.reportData = reportData; }

    public String getReportName() { return reportName; }
    public void setReportName(String reportName) { this.reportName = reportName; }
}