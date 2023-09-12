package org.eclipse.mosaic.app.sdnvfn.information;

import java.util.ArrayList;
import java.util.HashMap;

//
// Obs. common services and specific services has got from configuration file and informed by the ServerApp**/
public class GlobalDynamicVehicleMap extends DynamicVehicleMap{
    private final ArrayList<String> commonVehicleServices;
    private final HashMap<String,String> specificVehicleServices;

    public GlobalDynamicVehicleMap(ArrayList<String> commonVehicleServices, ArrayList<String> specificVehicleServices) {
        super();
        this.commonVehicleServices = commonVehicleServices;
        this.specificVehicleServices = convertSrvListToMap(specificVehicleServices);
    }
    @Override
    public void addVehicleInfo(HashMap<String, String> mappedMsg){
        RsuConnectedVehicle vehicleInfo;
        if(!vehiclesMap.containsKey(mappedMsg.get("vhId"))){
            //First register of the vehicle: allocate a new object to represent vehicle data and add its authorized services
            vehiclesMap.put(mappedMsg.get("vhId"),new RsuConnectedVehicle());
            addCommonServicesToVehicle(mappedMsg.get("vhId"));
            addSpecificServicesToVehicle(mappedMsg.get("vhId"));
        }
        vehicleInfo = vehiclesMap.get(mappedMsg.get("vhId"));
        vehicleInfo.setVehicleId(mappedMsg.get("vhId"));
        vehicleInfo.setSpeed(Double.parseDouble(mappedMsg.get("speed")));
        vehicleInfo.setLatitude(Double.parseDouble(mappedMsg.get("latitude")));
        vehicleInfo.setLongitude(Double.parseDouble(mappedMsg.get("longitude")));
        vehicleInfo.setRsuAcessPoint(mappedMsg.get("rsuId"));
    }

    private void addCommonServicesToVehicle(String vehicleId){
        RsuConnectedVehicle vehicleData = this.vehiclesMap.get(vehicleId);
        for (String service: this.commonVehicleServices) {
            vehicleData.addService(service,"unused"); //added with no rsuId
        }
    }

    private void addSpecificServicesToVehicle(String vehicleId){
        if(this.specificVehicleServices.containsKey(vehicleId)){
            RsuConnectedVehicle vehicleData = this.vehiclesMap.get(vehicleId);
            //Extrair os serviços específicos para o veículo. Formato de entrada "service_03:service_04"
            //only add specific services if it was defined in config file
            String[] services = this.specificVehicleServices.get(vehicleId).split(":");
            //se houver pelo menos 1 serviço específico para o veículo em questão, adiciona na lista de serviços do mesmo.
            if(services.length>0){
                for (String service: services) {
                    vehicleData.addService(service,"unused"); //added with no rsuId
                }
            }
        }

    }


    private HashMap<String,String> convertSrvListToMap(ArrayList<String> specificSrvList){
        //this method receive a vehicle-specific service List and convert to a hashmap
        //Cada item do Array possui uma String no formato "veh_01=service_03:service_04"

        HashMap<String,String> specificSrvMaps = new HashMap<>();
        for (String vehicleServices: specificSrvList) {
            String[] specificSrvInfo = vehicleServices.split("=");
            specificSrvMaps.put(specificSrvInfo[0],specificSrvInfo[1]);
        }
        return specificSrvMaps;
    }




}
