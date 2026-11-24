package com.accesolibre.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GeofenceServiceTest {

    private final GeofenceService geofenceService = new GeofenceService();

    @Test
    void shouldDetectCoordinatesInsideAndOutsideRadius() {
        double condoLat = -34.603722;
        double condoLon = -58.381592;

        // ~45 meters north
        assertThat(geofenceService.isWithinRadius(condoLat, condoLon, -34.603320, -58.381592, 150)).isTrue();

        // ~1.1 km away
        assertThat(geofenceService.isWithinRadius(condoLat, condoLon, -34.613722, -58.381592, 150)).isFalse();
    }
}
