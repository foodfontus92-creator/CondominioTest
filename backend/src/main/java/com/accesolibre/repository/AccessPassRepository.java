package com.accesolibre.repository;

import com.accesolibre.domain.entity.AccessPass;
import com.accesolibre.domain.enums.PassStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccessPassRepository extends JpaRepository<AccessPass, UUID> {

    Optional<AccessPass> findByIdAndCondominiumId(UUID id, UUID condominiumId);

    Page<AccessPass> findAllByCondominiumIdOrderByCreatedAtDesc(UUID condominiumId, Pageable pageable);

    Page<AccessPass> findAllByCondominiumIdAndUnitIdOrderByCreatedAtDesc(UUID condominiumId, UUID unitId, Pageable pageable);

    List<AccessPass> findAllByCondominiumIdAndUnitIdAndStatusOrderByCreatedAtDesc(
            UUID condominiumId,
            UUID unitId,
            PassStatus status
    );
}
