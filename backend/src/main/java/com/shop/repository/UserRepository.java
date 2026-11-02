package com.shop.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.shop.entity.User;

public interface UserRepository extends JpaRepository<User, Long> {

    @EntityGraph(attributePaths = "roles")
    Optional<User> findByEmail(String email);

    @EntityGraph(attributePaths = "roles")
    Optional<User> findWithRolesById(Long id);

    boolean existsByEmail(String email);

    boolean existsByPhone(String phone);

    @Query("select count(u) > 0 from User u join u.roles r where r.name = :roleName")
    boolean existsWithRole(@Param("roleName") String roleName);
}
