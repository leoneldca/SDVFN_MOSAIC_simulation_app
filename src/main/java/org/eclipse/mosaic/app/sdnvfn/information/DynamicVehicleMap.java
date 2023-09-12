package org.eclipse.mosaic.app.sdnvfn.information;

import java.util.HashMap;
import java.util.Objects;

public class DynamicVehicleMap {
    protected final HashMap<String, RsuConnectedVehicle> vehiclesMap;

    public DynamicVehicleMap() {
        this.vehiclesMap = new HashMap<>();
    } //construtor da classe

    public HashMap<String, RsuConnectedVehicle> getVehiclesMap(){
        return this.vehiclesMap;
    }

    public void addVehicleInfo(HashMap<String, String> mappedMsg){
        RsuConnectedVehicle vehicleInfo;
        if(!vehiclesMap.containsKey(mappedMsg.get("vhId"))){
            //First register of the vehicle: allocate a new object to represent vehicle data and add its authorized services
            vehiclesMap.put(mappedMsg.get("vhId"),new RsuConnectedVehicle());
        }
        vehicleInfo = vehiclesMap.get(mappedMsg.get("vhId"));
        vehicleInfo.updateVehicleData(mappedMsg);

    }

    public Boolean isRsuRecordedToService(String vhId, String service){
        if(vehiclesMap.containsKey(vhId)){
            RsuConnectedVehicle vehicleData = this.vehiclesMap.get(vhId);
            return !Objects.equals(vehicleData.getRsuOfservice(service), "unused");
        }
        return false;
    }

}
