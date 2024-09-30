package org.eclipse.mosaic.app.sdnvfn.information;

import org.eclipse.mosaic.app.sdnvfn.config.VehicleConfig;
import org.eclipse.mosaic.lib.objects.vehicle.VehicleData;
import org.eclipse.mosaic.rti.TIME;

import java.util.*;

/**
 * List of RSUs in contact with a specific vehicle
 */
public class PriorityConnectedRsuList {

    private final LinkedList<RsuAnnouncedInfo> priorityRsuList = new LinkedList<>();
    VehicleConfig vehicleConfig;
    Float maxHeadingDifference;

    public PriorityConnectedRsuList(VehicleConfig vehicleConfig, Float maxHeadingDifference){
        this.vehicleConfig = vehicleConfig;
        this.maxHeadingDifference = maxHeadingDifference;
    }

    //Fazer método de update da lista com o cálculo da distância dos veículos.

    /**
     *
     * @param announcedRsu
     */
    public void updatePriotyList(RsuAnnouncedInfo announcedRsu, VehicleData vehicleData, Float maxDistance){
        boolean rsuFound = false;

        double relativeSpeed;
        double timeToReachRsu;
        if(!this.priorityRsuList.isEmpty()){
            //Atualiza a distância do RSU na lista
            RsuAnnouncedInfo rsu;
            int index = 0;
            //Atualiza os dados de cada um dos RSUs
            while (index < priorityRsuList.size()){
                rsu = priorityRsuList.get(index);
                rsu.setDistanceToVehicle(vehicleData.getPosition().getLatitude(),vehicleData.getPosition().getLatitude());
                rsu.setHeadingDiferenceToVehicle(vehicleData);
                relativeSpeed = calculateRelativeSpeed(rsu.getHeadingDiferenceToVehicle());
                timeToReachRsu = rsu.getDistanceToVehicle() / relativeSpeed;
                rsu.setTimeToReachRsu(timeToReachRsu); //set relative time to vehicle to reach this RSU

                if(Objects.equals(rsu.getRsuId(), announcedRsu.getRsuId())){ //Se o remetende do RSUBeacon já está na lista, atualizar o instante de chegada do Beacon, pois demais elementos já foram atualizados
                    rsu.setBeaconArrivedTime(announcedRsu.getBeaconArrivedTime());
                    rsuFound = true;
                }
                //se o RSU não envia beacons a mais de 3 segundos ou se o HeadingDifference está acima do máximo, remover o RSU da lista.
                if((rsu.getBeaconArrivedTime()+3*TIME.SECOND)<announcedRsu.getBeaconArrivedTime()
                        || rsu.getHeadingDiferenceToVehicle()>this.maxHeadingDifference
                        || rsu.getDistanceToVehicle()>maxDistance)
                    {
                    priorityRsuList.remove(index);
                }else{
                    index++;
                }

            }


        }
        if(!rsuFound){ //Se o RSU anunciado não está na lista, adicionar. Não ver maxheading diference neste caso.
            announcedRsu.setDistanceToVehicle(vehicleData.getPosition().getLatitude(),vehicleData.getPosition().getLatitude());
            announcedRsu.setHeadingDiferenceToVehicle(vehicleData);
            relativeSpeed = calculateRelativeSpeed(announcedRsu.getHeadingDiferenceToVehicle());
            timeToReachRsu = announcedRsu.getDistanceToVehicle() / relativeSpeed;
            announcedRsu.setTimeToReachRsu(timeToReachRsu);
            this.priorityRsuList.addFirst(announcedRsu);
        }

        //Após o final a lista não estará ordenada. Deve-se chamar o método de ordenação
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

    private double calculateRelativeSpeed(double headingDiference) {
        // Assuming a simple linear relationship between heading difference and speed
        //
        return vehicleConfig.fixedVirtualSpeed-(vehicleConfig.headingDiferenceWeigth*headingDiference);
    }

}
