package org.eclipse.mosaic.app.sdnvfn.information;

import java.util.HashMap;

/**
 * A Classe armazenada dados recebidos de um único veículo, contendo ID, Speed, Latitude, Longitude. T
 */
public class RsuConnectedVehicle {
    private String vhId;
    private String rsuId;
    private double speed;
    private double latitude;
    private double longitude;
    private HashMap<String, String> serviceRsuMap;  //Map a service to a RSU


    public RsuConnectedVehicle() {
        this.vhId = "";
        this.rsuId = "";
        this.speed = 0D;
        this.latitude = 0D;
        this.longitude = 0D;
        this.serviceRsuMap = new HashMap<>(); //sempre que um novo veículo é adicionado, obrigatoriamente ele terá uma lista de serviços
    }

    public void updateVehicleData(HashMap<String, String> vhInfoMap){
        this.setVehicleId(vhInfoMap.get("vhId"));
        this.setSpeed(Double.parseDouble(vhInfoMap.get("speed")));
        this.setLatitude(Double.parseDouble(vhInfoMap.get("latitude")));
        this.setLongitude(Double.parseDouble(vhInfoMap.get("longitude")));
        this.setRsuAcessPoint(vhInfoMap.get("rsuId"));
    }


    public String getVechicleId() {
        return vhId;
    }
    public void setVehicleId(String vhId){
        this.vhId = vhId;
    }

    public void setRsuAcessPoint(String rsuId)
    {
        this.rsuId = rsuId;
    }


    public double getSpeed() {
        return speed;
    }

    public  void setSpeed(double speed){
        this.speed = speed;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude){
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude){
        this.longitude = longitude;
    }

    public HashMap<String, String> getServiceRsuMap() {
        return serviceRsuMap;
    }

    public void addService(String serviceId, String rsuId){
        this.serviceRsuMap.put(serviceId,rsuId); //adiciona o serviço.
    }

    public Boolean isServiceMapEmpty(){
        return serviceRsuMap.isEmpty();
    }


    public void removeVehicleService(String serviceId){
        serviceRsuMap.remove(serviceId);
    }

    public void setRsuServiceRunner(String serviceId, String rsuRunnerId){

        if(this.serviceRsuMap.containsKey(serviceId)){
            serviceRsuMap.replace(serviceId,rsuRunnerId);
        }else{
            serviceRsuMap.put(serviceId,rsuRunnerId);
        }

    }

    public String getRsuOfservice(String serviceId){
        if (this.serviceRsuMap.containsKey(serviceId)){
            return serviceRsuMap.get(serviceId);
        }
        return "-1";
    }



    @Override
    public String toString() {
        return "RsuConnectedVehicle{" +
                "vhId='" + this.vhId + '\'' +
                ", rsuId=" + this.rsuId +
                ", speed=" + this.speed +
                ", latitude=" + this.latitude +
                ", longitude=" + this.longitude +
                ", services=" + this.serviceRsuMap.toString() +
                '}';
    }
}
