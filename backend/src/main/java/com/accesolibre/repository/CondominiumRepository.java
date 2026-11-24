package com.accesolibre.repository;

import com.accesolibre.domain.entity.Condominium;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CondominiumRepository extends JpaRepository<Condominium, UUID> {

    Optional<Condominium> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);

    List<Condominium> findAllByActiveTrueOrderByNameAsc();
}
