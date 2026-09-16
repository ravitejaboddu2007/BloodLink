package com.bloodlink.dto;

import java.util.List;

public class CreateDonorAlertsRequest {

    public static class DonorAlertItem {
        private String donorId;
        private String alertType; // "direct", "waiting"

        public DonorAlertItem() {}

        public DonorAlertItem(String donorId, String alertType) {
            this.donorId = donorId;
            this.alertType = alertType;
        }

        public String getDonorId() { return donorId; }
        public void setDonorId(String donorId) { this.donorId = donorId; }

        public String getAlertType() { return alertType; }
        public void setAlertType(String alertType) { this.alertType = alertType; }
    }

    private List<DonorAlertItem> donors;
    private Integer remainingUnitsAtDonorAlert;

    public CreateDonorAlertsRequest() {}

    public List<DonorAlertItem> getDonors() { return donors; }
    public void setDonors(List<DonorAlertItem> donors) { this.donors = donors; }

    public Integer getRemainingUnitsAtDonorAlert() { return remainingUnitsAtDonorAlert; }
    public void setRemainingUnitsAtDonorAlert(Integer remainingUnitsAtDonorAlert) {
        this.remainingUnitsAtDonorAlert = remainingUnitsAtDonorAlert;
    }
}
