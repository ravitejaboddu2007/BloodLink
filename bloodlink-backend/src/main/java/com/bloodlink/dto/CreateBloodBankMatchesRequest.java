package com.bloodlink.dto;

import java.util.List;

public class CreateBloodBankMatchesRequest {

    private List<BloodBankMatchItem> bloodBanks;

    public CreateBloodBankMatchesRequest() {}

    public CreateBloodBankMatchesRequest(List<BloodBankMatchItem> bloodBanks) {
        this.bloodBanks = bloodBanks;
    }

    public List<BloodBankMatchItem> getBloodBanks() {
        return bloodBanks;
    }

    public void setBloodBanks(List<BloodBankMatchItem> bloodBanks) {
        this.bloodBanks = bloodBanks;
    }

    public static class BloodBankMatchItem {
        private String bloodBankId;
        private Double distance;
        private Integer availableUnitsAtMatch;
        private String status; // "sufficient", "partial"
        private String responseStatus; // "pending", "waiting"

        public BloodBankMatchItem() {}

        public BloodBankMatchItem(String bloodBankId, Double distance, Integer availableUnitsAtMatch, String status, String responseStatus) {
            this.bloodBankId = bloodBankId;
            this.distance = distance;
            this.availableUnitsAtMatch = availableUnitsAtMatch;
            this.status = status;
            this.responseStatus = responseStatus;
        }

        public String getBloodBankId() {
            return bloodBankId;
        }

        public void setBloodBankId(String bloodBankId) {
            this.bloodBankId = bloodBankId;
        }

        public Double getDistance() {
            return distance;
        }

        public void setDistance(Double distance) {
            this.distance = distance;
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
    }
}
