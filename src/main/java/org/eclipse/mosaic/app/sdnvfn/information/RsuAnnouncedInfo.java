package org.eclipse.mosaic.app.sdnvfn.information;

import org.eclipse.mosaic.app.sdnvfn.utils.NodesUtils;
import org.eclipse.mosaic.lib.geo.MutableGeoPoint;
import org.jetbrains.annotations.NotNull;

public class RsuAnnouncedInfo implements Comparable<RsuAnnouncedInfo>{
    private String rsuId;
    private Double latRsu;
    private Double longRsu;
    private Double distanceToVehicle;
    private long beaconArrivedTime;

    public RsuAnnouncedInfo(String rsuId, Double latRsu, Double longRsu) {
        this.setRsuId(rsuId);
        this.setLatRsu(latRsu);
        this.setLongRsu(longRsu);
        this.setDistanceToVehicle(Double.MAX_VALUE);
    }

    public Double getDistanceToVehicle() {
        return distanceToVehicle;
    }



    public void setDistanceToVehicle(double latVehicle, double longVehicle){

        this.distanceToVehicle = NodesUtils.calculateVehicleRsuDistance(
                new MutableGeoPoint(latVehicle,longVehicle),
                new MutableGeoPoint(this.latRsu,this.longRsu));
    }

    public void setDistanceToVehicle(Double distanceToVehicle) {
        this.distanceToVehicle = distanceToVehicle;
    }

    public String getRsuId(){
        return this.rsuId;
    }

    public Double getLatRsu() {
        return this.latRsu;
    }

    public Double getLongRsu() {
        return this.longRsu;
    }

    public void setRsuId(String rsuId) {
        this.rsuId = rsuId;
    }

    public void setLatRsu(Double latRsu) {
        this.latRsu = latRsu;
    }

    public void setLongRsu(Double longRsu) {
        this.longRsu = longRsu;
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
