package org.eclipse.mosaic.app.sdnvfn.information;

import org.jetbrains.annotations.NotNull;

import java.sql.Time;
import java.sql.Timestamp;
import java.util.HashMap;

public class RsuAnnouncedInfo implements Comparable<RsuAnnouncedInfo>{
    private String rsuId;
    private Double latitude;
    private Double longitude;
    private Double distanceToVehicle;

    private long beaconArrivedTime;

    public RsuAnnouncedInfo(String rsuId, Double latitude, Double longitude) {
        this.setRsuId(rsuId);
        this.setLatitude(latitude);
        this.setLongitude(longitude);
        this.setDistanceToVehicle(0D);
    }

    public Double getDistanceToVehicle() {
        return distanceToVehicle;
    }



    public void setDistanceToVehicle(Double vehicleLatitude, Double vehicleLongitude){
        Double dLatitude = this.latitude - vehicleLatitude;
        Double dLongitude = this.longitude - vehicleLongitude;

        this.distanceToVehicle = Math.sqrt(dLatitude*dLatitude + dLongitude*dLongitude);

    }

    public void setDistanceToVehicle(Double distanceToVehicle) {
        this.distanceToVehicle = distanceToVehicle;
    }

    public String getRsuId(){
        return this.rsuId;
    }

    public Double getLatitude() {
        return this.latitude;
    }

    public Double getLongitude() {
        return this.longitude;
    }

    public void setRsuId(String rsuId) {
        this.rsuId = rsuId;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public void setBeaconArrivedTime(long beaconArrivedTime){
        this.beaconArrivedTime = beaconArrivedTime;
    }

    public long getBeaconArrivedTime(){
        return this.beaconArrivedTime;
    }

    @Override
    public int compareTo(@NotNull RsuAnnouncedInfo otherRsu) {
        return this.distanceToVehicle.compareTo(otherRsu.getDistanceToVehicle());
    }

}
