package com.bloodlink.dto;

import java.util.LinkedHashMap;
import java.util.Map;

public class MatchedBloodBankItemDTO {
    private String id;
    private String name;
    private String registrationNumber;
    private String phone;
    private String address;
    private String city;
    private String state;
    private Double distance;
    private String bloodGroup;
    private Integer availableUnitsAtMatch;
    private String status;
    private String responseStatus;
    private Integer unitsSecured = 0;
    private String respondedAt;
    private Map<String, Integer> compatibleStock = new LinkedHashMap<>();

    public MatchedBloodBankItemDTO() {}

    public MatchedBloodBankItemDTO(String id, String name, String registrationNumber, String phone,
                                  String address, String city, String state, Double distance,
                                  String bloodGroup, Integer availableUnitsAtMatch, String status,
                                  String responseStatus, Integer unitsSecured, String respondedAt) {
        this(id, name, registrationNumber, phone, address, city, state, distance, bloodGroup,
             availableUnitsAtMatch, status, responseStatus, unitsSecured, respondedAt, new LinkedHashMap<>());
    }

    public MatchedBloodBankItemDTO(String id, String name, String registrationNumber, String phone,
                                  String address, String city, String state, Double distance,
                                  String bloodGroup, Integer availableUnitsAtMatch, String status,
                                  String responseStatus, Integer unitsSecured, String respondedAt,
                                  Map<String, Integer> compatibleStock) {
        this.id = id;
        this.name = name;
        this.registrationNumber = registrationNumber;
        this.phone = phone;
        this.address = address;
        this.city = city;
        this.state = state;
        this.distance = distance;
        this.bloodGroup = bloodGroup;
        this.availableUnitsAtMatch = availableUnitsAtMatch;
        this.status = status;
        this.responseStatus = responseStatus;
        this.unitsSecured = unitsSecured != null ? unitsSecured : 0;
        this.respondedAt = respondedAt;
        this.compatibleStock = compatibleStock != null ? compatibleStock : new LinkedHashMap<>();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getRegistrationNumber() {
        return registrationNumber;
    }

    public void setRegistrationNumber(String registrationNumber) {
        this.registrationNumber = registrationNumber;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public Double getDistance() {
        return distance;
    }

    public void setDistance(Double distance) {
        this.distance = distance;
    }

    public String getBloodGroup() {
        return bloodGroup;
    }

    public void setBloodGroup(String bloodGroup) {
        this.bloodGroup = bloodGroup;
    }

    public Integer getAvailableUnitsAtMatch() {
        return availableUnitsAtMatch;
    }

    public void setAvailableUnitsAtMatch(Integer availableUnitsAtMatch) {
        this.availableUnitsAtMatch = availableUnitsAtMatch;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getResponseStatus() {
        return responseStatus;
    }

    public void setResponseStatus(String responseStatus) {
        this.responseStatus = responseStatus;
    }

    public Integer getUnitsSecured() {
        return unitsSecured;
    }

    public void setUnitsSecured(Integer unitsSecured) {
        this.unitsSecured = unitsSecured;
    }

    public String getRespondedAt() {
        return respondedAt;
    }

    public void setRespondedAt(String respondedAt) {
        this.respondedAt = respondedAt;
    }

    public Map<String, Integer> getCompatibleStock() {
        return compatibleStock;
    }

    public void setCompatibleStock(Map<String, Integer> compatibleStock) {
        this.compatibleStock = compatibleStock != null ? compatibleStock : new LinkedHashMap<>();
    }
}