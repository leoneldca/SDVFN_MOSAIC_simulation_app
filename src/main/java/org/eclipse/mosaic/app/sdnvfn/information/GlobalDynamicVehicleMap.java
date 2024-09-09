package org.eclipse.mosaic.app.sdnvfn.information;

import java.util.ArrayList;
import java.util.HashMap;

//
// Obs. common services and specific services has got from configuration file and informed by the ServerApp**/
public class GlobalDynamicVehicleMap extends DynamicVehicleMap{

    private HashMap<String, VfnConnectedVehicle> vfnVehiclesMap; //cria uma tabela contendo o id do veículo e seu conjunto de dados
    private final ArrayList<String> commonVehicleServices;
    private final HashMap<String,String> specificVehicleServices;

    public GlobalDynamicVehicleMap(ArrayList<String> commonVehicleServices, ArrayList<String> specificVehicleServices) {
        super();
        this.vfnVehiclesMap = new HashMap<>();
        this.commonVehicleServices = commonVehicleServices;
        this.specificVehicleServices = convertSrvListToMap(specificVehicleServices);
    }

    public HashMap<String, VfnConnectedVehicle> getVfnVehiclesMap(){
        return this.vfnVehiclesMap;
    }

    /**
     * Método adiciona ou atualiza informações do veículo connectado à GlobalDynamicMap
     * @param mappedMsg
     */
    @Override
    public void addVehicleInfo(HashMap<String, String> mappedMsg){
        VfnConnectedVehicle vehicleInfo;
        if(!vfnVehiclesMap.containsKey(mappedMsg.get("vhId"))){
            //First register of the vehicle: allocate a new object to represent vehicle data and add its authorized services
            vfnVehiclesMap.put(mappedMsg.get("vhId"),new VfnConnectedVehicle());
            addCommonServicesToVehicle(mappedMsg.get("vhId"));
            addSpecificServicesToVehicle(mappedMsg.get("vhId"));
        }
        vehicleInfo = vfnVehiclesMap.get(mappedMsg.get("vhId"));
        vehicleInfo.updateVehicleData(mappedMsg);
    }

    private void addCommonServicesToVehicle(String vehicleId){
        VfnConnectedVehicle vehicleData = this.vfnVehiclesMap.get(vehicleId);
        for (String service: this.commonVehicleServices) {
            vehicleData.addService(service,"unused"); //added with no rsuId
        }
    }

    private void addSpecificServicesToVehicle(String vehicleId){
        if(this.specificVehicleServices.containsKey(vehicleId)){
            VfnConnectedVehicle connectedVehicle = this.vfnVehiclesMap.get(vehicleId);
            //Extrair os serviços específicos para o veículo. Formato de entrada "service_03:service_04"
            //only add specific services if it was defined in config file
            String[] services = this.specificVehicleServices.get(vehicleId).split(":");
            //se houver pelo menos 1 serviço específico para o veículo em questão, adiciona na lista de serviços do mesmo.
            if(services.length>0){
                for (String service: services) {
                    connectedVehicle.addService(service,"unused"); //added with no rsuId
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
