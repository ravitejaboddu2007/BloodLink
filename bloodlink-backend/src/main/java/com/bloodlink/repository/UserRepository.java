package com.bloodlink.repository;

import com.bloodlink.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, String> {
    boolean existsByEmail(String email);
    Optional<User> findByEmail(String email);
    Optional<User> findByEmailIgnoreCase(String email);
    boolean existsByRoleAndRegistrationNumberIgnoreCase(String role, String registrationNumber);
    List<User> findByRoleIgnoreCase(String role);
    List<User> findByRoleIgnoreCaseAndAvailableTrue(String role);
    List<User> findByRoleIgnoreCaseAndLatIsNotNullAndLngIsNotNull(String role);
    long countByRoleIgnoreCaseAndAvailableTrue(String role);
    long countByRoleIgnoreCase(String role);
}

