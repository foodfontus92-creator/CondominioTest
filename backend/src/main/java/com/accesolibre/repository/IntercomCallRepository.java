package com.accesolibre.repository;

import com.accesolibre.domain.entity.IntercomCall;
import com.accesolibre.domain.enums.IntercomCallStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IntercomCallRepository extends JpaRepository<IntercomCall, UUID> {

    Optional<IntercomCall> findByIdAndCondominiumId(UUID id, UUID condominiumId);

    Page<IntercomCall> findAllByCondominiumIdOrderByStartedAtDesc(UUID condominiumId, Pageable pageable);

    Page<IntercomCall> findAllByCondominiumIdAndUnitIdOrderByStartedAtDesc(UUID condominiumId, UUID unitId, Pageable pageable);

    List<IntercomCall> findAllByCondominiumIdAndUnitIdAndStatusInOrderByStartedAtDesc(
            UUID condominiumId,
            UUID unitId,
            Collection<IntercomCallStatus> statuses
    );
}
