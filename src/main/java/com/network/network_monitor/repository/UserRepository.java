package com.network.network_monitor.repository;

import java.util.List;
import java.util.Optional;

import com.network.network_monitor.entity.User;
import com.network.network_monitor.enums.Role;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    List<User> findByRole(Role role);
}