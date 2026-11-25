package com.accesolibre.service;

import com.accesolibre.config.AppProperties;
import com.accesolibre.domain.entity.Condominium;
import com.accesolibre.domain.entity.Unit;
import com.accesolibre.domain.enums.UserRole;
import com.accesolibre.dto.unit.BatchCreateUnitsRequest;
import com.accesolibre.dto.unit.BatchCreateUnitsResponse;
import com.accesolibre.exception.ConflictException;
import com.accesolibre.repository.CondominiumRepository;
import com.accesolibre.repository.UnitRepository;
import com.accesolibre.security.AuthenticatedUser;
import com.accesolibre.security.TenantAccessGuard;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UnitBatchServiceTest {

    private UUID condoId;
    private final Map<UUID, Unit> storedUnits = new LinkedHashMap<>();
    private UnitService unitService;

    @BeforeEach
    void setUp() {
        condoId = UUID.randomUUID();
        storedUnits.clear();

        Condominium condo = new Condominium();
        condo.setId(condoId);
        condo.setCode("los-aromos");
        condo.setName("Población Los Aromos");
        condo.setHmacSecret("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
        condo.setActive(true);

        AuthenticatedUser admin = new AuthenticatedUser(
                UUID.randomUUID(), condoId, null, "admin@losaromos.local", UserRole.CONDO_ADMIN);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(admin, null, admin.authorities())
        );

        CondominiumRepository condoRepo = (CondominiumRepository) Proxy.newProxyInstance(
                CondominiumRepository.class.getClassLoader(),
                new Class<?>[]{CondominiumRepository.class},
                (p, m, a) -> switch (m.getName()) {
                    case "findById" -> condoId.equals(a[0]) ? Optional.of(condo) : Optional.empty();
                    default -> throw new UnsupportedOperationException("CondominiumRepository." + m.getName());
                }
        );

        UnitRepository unitRepo = (UnitRepository) Proxy.newProxyInstance(
                UnitRepository.class.getClassLoader(),
                new Class<?>[]{UnitRepository.class},
                (p, m, a) -> switch (m.getName()) {
                    case "findAllByCondominiumIdOrderByUnitNumberAsc" -> storedUnits.values().stream()
                            .filter(u -> u.getCondominiumId().equals(a[0]))
                            .toList();
                    case "saveAll" -> {
                        @SuppressWarnings("unchecked")
                        Iterable<Unit> batch = (Iterable<Unit>) a[0];
                        List<Unit> out = new ArrayList<>();
                        for (Unit u : batch) {
                            if (u.getId() == null) u.setId(UUID.randomUUID());
                            storedUnits.put(u.getId(), u);
                            out.add(u);
                        }
                        yield out;
                    }
                    default -> throw new UnsupportedOperationException("UnitRepository." + m.getName());
                }
        );

        unitService = new UnitService(unitRepo, condoRepo, new CompactQrCryptoService(new AppProperties()), new TenantAccessGuard());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void allowsSameNumbersInDifferentBlocksAndRejectsDuplicateInsideSameBlock() {
        BatchCreateUnitsResponse blockB = unitService.createUnitsBatch(
                condoId,
                new BatchCreateUnitsRequest("Block B", List.of("101", "102", "201", "202", "101"))
        );
        assertThat(blockB.blockName()).isEqualTo("Block B");
        assertThat(blockB.createdCount()).isEqualTo(4);
        assertThat(blockB.units()).allSatisfy(u -> assertThat(u.blockName()).isEqualTo("Block B"));

        BatchCreateUnitsResponse blockC = unitService.createUnitsBatch(
                condoId,
                new BatchCreateUnitsRequest("Block C", List.of("101", "102", "201", "202"))
        );
        assertThat(blockC.blockName()).isEqualTo("Block C");
        assertThat(blockC.createdCount()).isEqualTo(4);
        assertThat(storedUnits).hasSize(8);

        assertThatThrownBy(() -> unitService.createUnitsBatch(
                condoId,
                new BatchCreateUnitsRequest("Block B", List.of("201", "301"))
        )).isInstanceOf(ConflictException.class);
    }
}
