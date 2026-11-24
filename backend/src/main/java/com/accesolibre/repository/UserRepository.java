package com.accesolibre.repository;

import com.accesolibre.domain.entity.User;
import com.accesolibre.domain.enums.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByIdAndCondominiumId(UUID id, UUID condominiumId);

    Optional<User> findByCondominiumIdAndEmailIgnoreCase(UUID condominiumId, String email);

    boolean existsByCondominiumIdAndEmailIgnoreCase(UUID condominiumId, String email);

    List<User> findAllByCondominiumIdOrderByFullNameAsc(UUID condominiumId);

    List<User> findAllByCondominiumIdAndUnitIdOrderByFullNameAsc(UUID condominiumId, UUID unitId);

    List<User> findAllByCondominiumIdAndRoleOrderByFullNameAsc(UUID condominiumId, UserRole role);
}
