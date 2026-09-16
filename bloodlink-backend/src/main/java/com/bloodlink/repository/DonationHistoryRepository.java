package com.bloodlink.repository;

import com.bloodlink.entity.DonationHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DonationHistoryRepository extends JpaRepository<DonationHistory, String> {
    List<DonationHistory> findByDonorId(String donorId);
    List<DonationHistory> findByDonorIdOrderByDateDesc(String donorId);
}