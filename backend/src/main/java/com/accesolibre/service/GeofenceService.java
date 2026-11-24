package com.accesolibre.service;

import org.springframework.stereotype.Service;

@Service
public class GeofenceService {

    private static final double EARTH_RADIUS_METERS = 6_371_000.0;

    /**
     * Computes great-circle distance in meters using the Haversine formula.
     */
    public double distanceMeters(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double radLat1 = Math.toRadians(lat1);
        double radLat2 = Math.toRadians(lat2);

        double a = Math.sin(dLat / 2.0) * Math.sin(dLat / 2.0)
                + Math.cos(radLat1) * Math.cos(radLat2)
                * Math.sin(dLon / 2.0) * Math.sin(dLon / 2.0);
        double c = 2.0 * Math.atan2(Math.sqrt(a), Math.sqrt(1.0 - a));
        return EARTH_RADIUS_METERS * c;
    }

    public boolean isWithinRadius(
            double targetLat,
            double targetLon,
            double userLat,
            double userLon,
            int radiusMeters
    ) {
        return distanceMeters(targetLat, targetLon, userLat, userLon) <= radiusMeters;
    }
}
