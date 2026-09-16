package com.bloodlink.dto;

import java.util.ArrayList;
import java.util.List;

public class MatchDonorsResponseDTO {
    private List<MatchedDonorItemDTO> direct = new ArrayList<>();
    private List<MatchedDonorItemDTO> waiting = new ArrayList<>();
    private Integer radiusUsed;
    private Boolean expanded = false;
    private Integer totalMatched = 0;

    public MatchDonorsResponseDTO() {}

    public MatchDonorsResponseDTO(List<MatchedDonorItemDTO> direct, List<MatchedDonorItemDTO> waiting, Integer radiusUsed, Boolean expanded, Integer totalMatched) {
        this.direct = direct != null ? direct : new ArrayList<>();
        this.waiting = waiting != null ? waiting : new ArrayList<>();
        this.radiusUsed = radiusUsed;
        this.expanded = expanded != null ? expanded : false;
        this.totalMatched = totalMatched != null ? totalMatched : 0;
    }

    public List<MatchedDonorItemDTO> getDirect() {
        return direct;
    }

    public void setDirect(List<MatchedDonorItemDTO> direct) {
        this.direct = direct;
    }

    public List<MatchedDonorItemDTO> getWaiting() {
        return waiting;
    }

    public void setWaiting(List<MatchedDonorItemDTO> waiting) {
        this.waiting = waiting;
    }

    public Integer getRadiusUsed() {
        return radiusUsed;
    }

    public void setRadiusUsed(Integer radiusUsed) {
        this.radiusUsed = radiusUsed;
    }

    public Boolean getExpanded() {
        return expanded;
    }

    public void setExpanded(Boolean expanded) {
        this.expanded = expanded;
    }

    public Integer getTotalMatched() {
        return totalMatched;
    }

    public void setTotalMatched(Integer totalMatched) {
        this.totalMatched = totalMatched;
    }
}
