package com.network.network_monitor.repository;

import java.util.List;
import java.util.Optional;

import com.network.network_monitor.entity.User;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    List<User> findByEnabledTrue();

    List<User> findByRolesNameIn(List<String> roleNames);
}