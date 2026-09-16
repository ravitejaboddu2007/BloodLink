package com.bloodlink.repository;

import com.bloodlink.entity.RequestDonor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RequestDonorRepository extends JpaRepository<RequestDonor, Long> {
    List<RequestDonor> findByDonorId(String donorId);
    List<RequestDonor> findByBloodRequestId(String requestId);
    Optional<RequestDonor> findByDonorIdAndBloodRequestId(String donorId, String requestId);
}
