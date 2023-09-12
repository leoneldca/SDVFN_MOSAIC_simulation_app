package org.eclipse.mosaic.app.sdnvfn.information;

import java.util.*;

/**
 * List of RSUs in contact with a specific vehicle
 */
public class PriorityConnectedRsuList {

    private final Integer MAX_LIST_SIZE = 5;

    private final LinkedList<RsuAnnouncedInfo> priorityRsuList = new LinkedList<>();

    public PriorityConnectedRsuList(){

    }

    //Fazer método de update da lista com o cálculo da distância dos veículos.
    public void insertAnnouncedRsu(RsuAnnouncedInfo announcedRsu){
        boolean rsuFound = false;
        if(!this.priorityRsuList.isEmpty()){
            //Atualiza a distância do RSU na lista
            for (RsuAnnouncedInfo rsu : priorityRsuList){
                if(Objects.equals(rsu.getRsuId(), announcedRsu.getRsuId())){ //se o RSU já está na lista, apenas atualizar distâncias
                    rsu.setDistanceToVehicle(announcedRsu.getDistanceToVehicle());
                    rsu.setBeaconArrivedTime(announcedRsu.getBeaconArrivedTime());
                    rsuFound = true;
                    break;
                }
            }


        }
        if(!rsuFound){
            //apenas adicionar o RSU na lista de RSUs conectados
            RsuAnnouncedInfo newRsuInfo = new RsuAnnouncedInfo(
                    announcedRsu.getRsuId(),
                    announcedRsu.getLatitude(),
                    announcedRsu.getLongitude()
            );
            newRsuInfo.setDistanceToVehicle(announcedRsu.getDistanceToVehicle());
            newRsuInfo.setBeaconArrivedTime(announcedRsu.getBeaconArrivedTime());
            this.priorityRsuList.addFirst(newRsuInfo);
        }

        Collections.sort(this.priorityRsuList); //Ordena a PriorityConnection colocando o escolhido no início da lista. Ver método compareTo da classe RsuAnnoucedInfo
        if(this.priorityRsuList.size()>MAX_LIST_SIZE) this.priorityRsuList.removeLast();
    }

    //método que atualiza as distâncias dos RSUs para os veículos.
    public void updateRsuDistances(Double vehicleLatitude, Double vehicleLongitude){
        if(!priorityRsuList.isEmpty()) {
            for (RsuAnnouncedInfo rsu : priorityRsuList) {
                rsu.setDistanceToVehicle(vehicleLatitude,vehicleLongitude);
            }
            Collections.sort(this.priorityRsuList);
        }
    }

    public LinkedList<RsuAnnouncedInfo> getRsuList(){
        return priorityRsuList;
    }

}
