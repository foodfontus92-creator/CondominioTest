package com.accesolibre.repository;

import com.accesolibre.domain.entity.IotDevice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IotDeviceRepository extends JpaRepository<IotDevice, UUID> {

    List<IotDevice> findAllByCondominiumIdOrderByNameAsc(UUID condominiumId);

    List<IotDevice> findAllByCondominiumIdAndActiveTrueOrderByNameAsc(UUID condominiumId);

    Optional<IotDevice> findByIdAndCondominiumId(UUID id, UUID condominiumId);

    Optional<IotDevice> findByPublicQrToken(String publicQrToken);

    boolean existsByHardwareUidIgnoreCase(String hardwareUid);

    boolean existsByMqttUsernameIgnoreCase(String mqttUsername);
}
