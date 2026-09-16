package com.bloodlink.dto;

public class MatchedDonorItemDTO {
    private String id;
    private String name;
    private String bloodGroup;
    private String phone;
    private String city;
    private String state;
    private Double distance;
    private Double rankScore;
    private String freshnessStatus;
    private String status;
    private String type;
    private String at;

    public MatchedDonorItemDTO() {}

    public MatchedDonorItemDTO(String id, String name, String bloodGroup, String phone,
                               String city, String state, Double distance, Double rankScore,
                               String freshnessStatus, String status, String type, String at) {
        this.id = id;
        this.name = name;
        this.bloodGroup = bloodGroup;
        this.phone = phone;
        this.city = city;
        this.state = state;
        this.distance = distance;
        this.rankScore = rankScore;
        this.freshnessStatus = freshnessStatus;
        this.status = status;
        this.type = type;
        this.at = at;
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

    public String getBloodGroup() {
        return bloodGroup;
    }

    public void setBloodGroup(String bloodGroup) {
        this.bloodGroup = bloodGroup;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
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

    public Double getRankScore() {
        return rankScore;
    }

    public void setRankScore(Double rankScore) {
        this.rankScore = rankScore;
    }

    public String getFreshnessStatus() {
        return freshnessStatus;
    }

    public void setFreshnessStatus(String freshnessStatus) {
        this.freshnessStatus = freshnessStatus;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getAt() {
        return at;
    }

    public void setAt(String at) {
        this.at = at;
    }
}
