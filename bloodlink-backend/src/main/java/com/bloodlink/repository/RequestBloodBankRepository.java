package com.bloodlink.repository;

import com.bloodlink.entity.RequestBloodBank;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RequestBloodBankRepository extends JpaRepository<RequestBloodBank, Long> {

    List<RequestBloodBank> findByBloodBankId(String bloodBankId);

    Optional<RequestBloodBank> findByBloodBankIdAndBloodRequestId(String bloodBankId, String bloodRequestId);

    List<RequestBloodBank> findByBloodRequestId(String bloodRequestId);

    List<RequestBloodBank> findByBloodRequestIdOrderByIdAsc(String bloodRequestId);
}
