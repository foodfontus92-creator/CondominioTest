package com.accesolibre.repository;

import com.accesolibre.domain.entity.AdminActivationCode;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface AdminActivationCodeRepository extends JpaRepository<AdminActivationCode, UUID> {

    boolean existsByCodeIgnoreCase(String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM AdminActivationCode c WHERE UPPER(c.code) = UPPER(:code)")
    Optional<AdminActivationCode> findByCodeForUpdate(@Param("code") String code);
}
