package com.fieldservice.util;

/**
 * Geographic utility functions for server-side location verification.
 * Calculates great-circle distances using the Haversine formula on a spherical Earth.
 */
public final class GeoUtils {

    public static final double EARTH_RADIUS_METERS = 6371000.0;
    public static final double DEFAULT_MAX_ACCURACY_THRESHOLD_METERS = 100.0;

    private GeoUtils() {}

    /**
     * Calculates the great-circle distance between two geographic coordinates in meters.
     */
    public static double calculateDistanceMeters(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(dLat / 2.0) * Math.sin(dLat / 2.0)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2.0) * Math.sin(dLon / 2.0);

        double c = 2.0 * Math.atan2(Math.sqrt(a), Math.sqrt(1.0 - a));
        return EARTH_RADIUS_METERS * c;
    }

    /**
     * Validates whether coordinate values are within legitimate WGS84 geographic bounds.
     */
    public static boolean isValidCoordinate(Double latitude, Double longitude) {
        if (latitude == null || longitude == null) {
            return false;
        }
        if (Double.isNaN(latitude) || Double.isInfinite(latitude) ||
            Double.isNaN(longitude) || Double.isInfinite(longitude)) {
            return false;
        }
        return latitude >= -90.0 && latitude <= 90.0 &&
               longitude >= -180.0 && longitude <= 180.0;
    }
}
