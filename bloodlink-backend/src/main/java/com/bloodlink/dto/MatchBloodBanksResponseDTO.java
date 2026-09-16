package com.bloodlink.dto;

import java.util.ArrayList;
import java.util.List;

public class MatchBloodBanksResponseDTO {
    private List<MatchedBloodBankItemDTO> bloodBanks = new ArrayList<>();
    private Integer totalMatched = 0;

    public MatchBloodBanksResponseDTO() {}

    public MatchBloodBanksResponseDTO(List<MatchedBloodBankItemDTO> bloodBanks, Integer totalMatched) {
        this.bloodBanks = bloodBanks != null ? bloodBanks : new ArrayList<>();
        this.totalMatched = totalMatched != null ? totalMatched : 0;
    }

    public List<MatchedBloodBankItemDTO> getBloodBanks() {
        return bloodBanks;
    }

    public void setBloodBanks(List<MatchedBloodBankItemDTO> bloodBanks) {
        this.bloodBanks = bloodBanks;
    }

    public Integer getTotalMatched() {
        return totalMatched;
    }

    public void setTotalMatched(Integer totalMatched) {
        this.totalMatched = totalMatched;
    }
}