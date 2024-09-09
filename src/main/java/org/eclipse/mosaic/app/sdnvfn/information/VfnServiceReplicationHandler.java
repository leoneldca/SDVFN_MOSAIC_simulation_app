package org.eclipse.mosaic.app.sdnvfn.information;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Class that represents the processor list of each RSU in a Vehicular Fog Network
 */
public class VfnServiceReplicationHandler {
    private final HashMap<String, ArrayList<String>> microserviceReplicationMap = new HashMap<>();

    private @Nullable ArrayList<String> getRsuListOfservice(String serviceId){

        if(!isReplicationMapEmpty()){
            if(microserviceReplicationMap.containsKey(serviceId)){
                return microserviceReplicationMap.get(serviceId);
            }
        }
        return null;
    }

    /**
     * recebe um serviço e o RSU que se deseja consulta.
     *
     * @param serviceId
     * @param rsuId
     * @return true se há uma replicação deste serviço no RSU consultado. False caso não exista.
     */
    public boolean isServiceInRsu(String serviceId, String rsuId){
        ArrayList<String> rsuList = getRsuListOfservice(serviceId);

        if (rsuList!=null){
            //existe uma lista para o servico requisitado
            if (rsuList.contains(rsuId)) return true;
            //se nesta lista contiver o RSU, então o referido RSU executa o processamento deste serviço.
        }

        return false;

    }


    public boolean isReplicationMapEmpty(){
        return microserviceReplicationMap.isEmpty();
    }

    /**
     *
     * @param serviceId
     * @param rsuId
     */
    public void addRsuToServiceRepList(String serviceId, String rsuId){

        if(!microserviceReplicationMap.containsKey(serviceId)){
            //adicionar a lista e linkar ao serviço serviço
            ArrayList<String> newRsuList = new ArrayList<>();
            newRsuList.add(rsuId);
            microserviceReplicationMap.put(serviceId,newRsuList);
        }else{
            //adição em lista pré-existente
            ArrayList<String> existedRsuList = microserviceReplicationMap.get(serviceId);
            existedRsuList.add(rsuId);

        }

    }




}
