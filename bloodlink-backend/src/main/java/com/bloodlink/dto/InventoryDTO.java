package com.bloodlink.dto;

import java.util.HashMap;
import java.util.Map;

public class InventoryDTO {

    private String bloodBankId;
    private Map<String, Integer> stock = new HashMap<>();
    private Map<String, Integer> reservedStock = new HashMap<>();
    private Map<String, String> lastUpdated = new HashMap<>();

    public InventoryDTO() {}

    public InventoryDTO(String bloodBankId, Map<String, Integer> stock, Map<String, String> lastUpdated) {
        this.bloodBankId = bloodBankId;
        this.stock = stock != null ? stock : new HashMap<>();
        this.lastUpdated = lastUpdated != null ? lastUpdated : new HashMap<>();
    }

    public InventoryDTO(String bloodBankId, Map<String, Integer> stock, Map<String, Integer> reservedStock, Map<String, String> lastUpdated) {
        this.bloodBankId = bloodBankId;
        this.stock = stock != null ? stock : new HashMap<>();
        this.reservedStock = reservedStock != null ? reservedStock : new HashMap<>();
        this.lastUpdated = lastUpdated != null ? lastUpdated : new HashMap<>();
    }

    public String getBloodBankId() {
        return bloodBankId;
    }

    public void setBloodBankId(String bloodBankId) {
        this.bloodBankId = bloodBankId;
    }

    public Map<String, Integer> getStock() {
        return stock;
    }

    public void setStock(Map<String, Integer> stock) {
        this.stock = stock;
    }

    public Map<String, Integer> getReservedStock() {
        return reservedStock;
    }

    public void setReservedStock(Map<String, Integer> reservedStock) {
        this.reservedStock = reservedStock;
    }

    public Map<String, String> getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(Map<String, String> lastUpdated) {
        this.lastUpdated = lastUpdated;
    }
}
