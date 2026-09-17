package com.bloodlink.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
public class User {

    @Id
    @Column(length = 64)
    private String id;

    @Column(nullable = false, length = 20)
    private String role; // "donor", "hospital", "bloodbank"

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, unique = true, length = 150)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(length = 20)
    private String phone;

    private Integer age;

    @Column(length = 20)
    private String gender;

    @Column(name = "blood_group", length = 10)
    private String bloodGroup;

    @Column(length = 100)
    private String city;

    @Column(length = 100)
    private String state;

    @Column(length = 255)
    private String address;

    @Column(name = "registration_number", length = 100)
    private String registrationNumber;

    @Column(name = "operating_hours", length = 100)
    private String operatingHours;

    private Double lat;
    private Double lng;

    @Column(name = "registered_lat")
    private Double registeredLat;

    @Column(name = "registered_lng")
    private Double registeredLng;

    @Column(name = "location_updated_at")
    private LocalDateTime locationUpdatedAt;

    @Column(name = "last_notified_at")
    private LocalDateTime lastNotifiedAt;

    private Boolean available;
    private Boolean suspended = false;

    @Column(name = "last_donation", length = 30)
    private String lastDonation;

    @Column(name = "next_eligible_date", length = 30)
    private String nextEligibleDate;

    @Lob
    @Column(name = "report_data", columnDefinition = "LONGTEXT")
    private String reportData;

    @Column(name = "report_name", length = 255)
    private String reportName;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public User() {}

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public Integer getAge() { return age; }
    public void setAge(Integer age) { this.age = age; }

    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }

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

    public Double getRegisteredLat() { return registeredLat; }
    public void setRegisteredLat(Double registeredLat) { this.registeredLat = registeredLat; }

    public Double getRegisteredLng() { return registeredLng; }
    public void setRegisteredLng(Double registeredLng) { this.registeredLng = registeredLng; }

    public LocalDateTime getLocationUpdatedAt() { return locationUpdatedAt; }
    public void setLocationUpdatedAt(LocalDateTime locationUpdatedAt) { this.locationUpdatedAt = locationUpdatedAt; }

    public LocalDateTime getLastNotifiedAt() { return lastNotifiedAt; }
    public void setLastNotifiedAt(LocalDateTime lastNotifiedAt) { this.lastNotifiedAt = lastNotifiedAt; }

    public Boolean getAvailable() { return available; }
    public void setAvailable(Boolean available) { this.available = available; }

    public Boolean getSuspended() { return suspended; }
    public boolean isSuspended() { return Boolean.TRUE.equals(suspended); }
    public void setSuspended(Boolean suspended) { this.suspended = suspended; }

    public String getLastDonation() { return lastDonation; }
    public void setLastDonation(String lastDonation) { this.lastDonation = lastDonation; }

    public String getNextEligibleDate() { return nextEligibleDate; }
    public void setNextEligibleDate(String nextEligibleDate) { this.nextEligibleDate = nextEligibleDate; }

    public boolean isEligibleToDonate() {
        if (nextEligibleDate != null && !nextEligibleDate.trim().isEmpty()) {
            try {
                java.time.LocalDate next = java.time.LocalDate.parse(nextEligibleDate.trim());
                return !java.time.LocalDate.now().isBefore(next);
            } catch (Exception ignored) {}
        }
        if (lastDonation != null && !lastDonation.trim().isEmpty()) {
            try {
                java.time.LocalDate last = java.time.LocalDate.parse(lastDonation.trim());
                int restingDays = "FEMALE".equalsIgnoreCase(this.gender) ? 112 : 84;
                java.time.LocalDate next = last.plusDays(restingDays);
                return !java.time.LocalDate.now().isBefore(next);
            } catch (Exception ignored) {}
        }
        return true;
    }

    public String getReportData() { return reportData; }
    public void setReportData(String reportData) { this.reportData = reportData; }

    public String getReportName() { return reportName; }
    public void setReportName(String reportName) { this.reportName = reportName; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
