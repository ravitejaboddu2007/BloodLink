package com.bloodlink.dto;

import java.util.Map;

public class UpdateInventoryRequest {

    private Map<String, Integer> stock;
    private Map<String, String> lastUpdated;

    public UpdateInventoryRequest() {}

    public UpdateInventoryRequest(Map<String, Integer> stock, Map<String, String> lastUpdated) {
        this.stock = stock;
        this.lastUpdated = lastUpdated;
    }

    public Map<String, Integer> getStock() {
        return stock;
    }

    public void setStock(Map<String, Integer> stock) {
        this.stock = stock;
    }

    public Map<String, String> getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(Map<String, String> lastUpdated) {
        this.lastUpdated = lastUpdated;
    }
}
