package com.accesolibre.repository;

import com.accesolibre.domain.entity.Unit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UnitRepository extends JpaRepository<Unit, UUID> {

    List<Unit> findAllByCondominiumIdOrderByUnitNumberAsc(UUID condominiumId);

    List<Unit> findAllByCondominiumIdAndActiveTrueOrderByUnitNumberAsc(UUID condominiumId);

    Optional<Unit> findByIdAndCondominiumId(UUID id, UUID condominiumId);

    Optional<Unit> findByCondominiumIdAndIntercomExtension(UUID condominiumId, String intercomExtension);

    boolean existsByCondominiumIdAndUnitNumberIgnoreCase(UUID condominiumId, String unitNumber);

    boolean existsByCondominiumIdAndIntercomExtension(UUID condominiumId, String intercomExtension);
}
