package com.bloodlink.repository;

import com.bloodlink.entity.BloodRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BloodRequestRepository extends JpaRepository<BloodRequest, String> {
    List<BloodRequest> findByHospitalId(String hospitalId);
    List<BloodRequest> findByHospitalIdOrderByCreatedAtDesc(String hospitalId);
}
