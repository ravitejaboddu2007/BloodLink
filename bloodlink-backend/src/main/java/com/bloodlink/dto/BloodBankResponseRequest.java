package com.bloodlink.dto;

public class BloodBankResponseRequest {

    private String status; // "accepted", "rejected"

    public BloodBankResponseRequest() {}

    public BloodBankResponseRequest(String status) {
        this.status = status;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
