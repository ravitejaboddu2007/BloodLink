package com.bloodlink.repository;

import com.bloodlink.entity.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory, Long> {
    Optional<Inventory> findByBloodBankId(String bloodBankId);
    boolean existsByBloodBankId(String bloodBankId);
}
