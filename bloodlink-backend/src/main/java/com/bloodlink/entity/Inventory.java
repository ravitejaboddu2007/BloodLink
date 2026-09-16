package com.bloodlink.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "inventory")
public class Inventory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "blood_bank_id", nullable = false, unique = true, length = 64)
    private String bloodBankId;

    @Column(name = "stock_a_pos", nullable = false)
    private Integer stockAPos = 0;

    @Column(name = "stock_a_neg", nullable = false)
    private Integer stockANeg = 0;

    @Column(name = "stock_b_pos", nullable = false)
    private Integer stockBPos = 0;

    @Column(name = "stock_b_neg", nullable = false)
    private Integer stockBNeg = 0;

    @Column(name = "stock_o_pos", nullable = false)
    private Integer stockOPos = 0;

    @Column(name = "stock_o_neg", nullable = false)
    private Integer stockONeg = 0;

    @Column(name = "stock_ab_pos", nullable = false)
    private Integer stockAbPos = 0;

    @Column(name = "stock_ab_neg", nullable = false)
    private Integer stockAbNeg = 0;

    @Column(name = "last_updated_json", length = 1000)
    private String lastUpdatedJson;

    public Inventory() {}

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getBloodBankId() { return bloodBankId; }
    public void setBloodBankId(String bloodBankId) { this.bloodBankId = bloodBankId; }

    public Integer getStockAPos() { return stockAPos; }
    public void setStockAPos(Integer stockAPos) { this.stockAPos = stockAPos; }

    public Integer getStockANeg() { return stockANeg; }
    public void setStockANeg(Integer stockANeg) { this.stockANeg = stockANeg; }

    public Integer getStockBPos() { return stockBPos; }
    public void setStockBPos(Integer stockBPos) { this.stockBPos = stockBPos; }

    public Integer getStockBNeg() { return stockBNeg; }
    public void setStockBNeg(Integer stockBNeg) { this.stockBNeg = stockBNeg; }

    public Integer getStockOPos() { return stockOPos; }
    public void setStockOPos(Integer stockOPos) { this.stockOPos = stockOPos; }

    public Integer getStockONeg() { return stockONeg; }
    public void setStockONeg(Integer stockONeg) { this.stockONeg = stockONeg; }

    public Integer getStockAbPos() { return stockAbPos; }
    public void setStockAbPos(Integer stockAbPos) { this.stockAbPos = stockAbPos; }

    public Integer getStockAbNeg() { return stockAbNeg; }
    public void setStockAbNeg(Integer stockAbNeg) { this.stockAbNeg = stockAbNeg; }

    public String getLastUpdatedJson() { return lastUpdatedJson; }
    public void setLastUpdatedJson(String lastUpdatedJson) { this.lastUpdatedJson = lastUpdatedJson; }
}
