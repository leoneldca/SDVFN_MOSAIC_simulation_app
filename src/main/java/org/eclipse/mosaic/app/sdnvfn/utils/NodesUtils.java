package org.eclipse.mosaic.app.sdnvfn.utils;

import org.eclipse.mosaic.lib.geo.GeoUtils;
import org.eclipse.mosaic.lib.geo.MutableGeoPoint;

public class NodesUtils {


    public static double calculateVehicleRsuDistance(double latVehicle, double longVehicle, double latRsu, double longRsu) {
        double R = 6371000; // Earth radius in meters
        double latDistance = Math.toRadians(latRsu - latVehicle); //N
        double longDistance = Math.toRadians(longRsu - longVehicle); //E

        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2) +
                Math.cos(Math.toRadians(latVehicle)) * Math.cos(Math.toRadians(latRsu)) *
                        Math.sin(longDistance / 2) * Math.sin(longDistance / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return R * c;
    }
    public static double calculateVehicleRsuDistance(MutableGeoPoint vehicleGeoLocation, MutableGeoPoint rsuGeoLocation) {

        return GeoUtils.distanceBetween(vehicleGeoLocation,rsuGeoLocation);
    }

    public static double calculateRelativeBearing(double lat1, double lon1, double lat2, double lon2) {
        double dLon = Math.toRadians(lon2 - lon1);
        double x = Math.sin(dLon) * Math.cos(Math.toRadians(lat2));
        double y = Math.cos(Math.toRadians(lat1)) * Math.sin(Math.toRadians(lat2))
                - Math.sin(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.cos(dLon);
        double bearing = Math.atan2(x, y);

        // Convert bearing from radians to degrees
        return Math.toDegrees(bearing);
    }

    public static double calculateRelativeBearing(MutableGeoPoint vehicleGeoPoint, MutableGeoPoint rsuGeoPoint) {
        return GeoUtils.azimuth(vehicleGeoPoint,rsuGeoPoint);
    }
    
    
}
