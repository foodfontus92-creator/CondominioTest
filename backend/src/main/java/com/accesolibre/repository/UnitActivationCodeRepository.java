package com.accesolibre.repository;

import com.accesolibre.domain.entity.UnitActivationCode;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UnitActivationCodeRepository extends JpaRepository<UnitActivationCode, UUID> {

    boolean existsByCodeIgnoreCase(String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM UnitActivationCode c WHERE UPPER(c.code) = UPPER(:code)")
    Optional<UnitActivationCode> findByCodeForUpdate(@Param("code") String code);

    @Modifying
    @Query("UPDATE UnitActivationCode c SET c.active = false " +
           "WHERE c.condominiumId = :condoId AND c.unitId = :unitId AND c.active = true")
    int deactivateActiveByUnit(@Param("condoId") UUID condoId, @Param("unitId") UUID unitId);
}
