package com.accesolibre.repository;

import com.accesolibre.domain.entity.AccessLog;
import com.accesolibre.domain.enums.AccessResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AccessLogRepository extends JpaRepository<AccessLog, UUID> {

    Page<AccessLog> findAllByCondominiumIdOrderByOccurredAtDesc(UUID condominiumId, Pageable pageable);

    Page<AccessLog> findAllByCondominiumIdAndUnitIdOrderByOccurredAtDesc(UUID condominiumId, UUID unitId, Pageable pageable);

    Page<AccessLog> findAllByCondominiumIdAndDeviceIdOrderByOccurredAtDesc(UUID condominiumId, UUID deviceId, Pageable pageable);

    Page<AccessLog> findAllByCondominiumIdAndResultOrderByOccurredAtDesc(UUID condominiumId, AccessResult result, Pageable pageable);
}
