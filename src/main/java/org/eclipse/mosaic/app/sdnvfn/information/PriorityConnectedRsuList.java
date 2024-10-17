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
    Float maxHeadingDifference; //cada lista criada possui uma maxheadingDifference

    public Float getMaxHeadingDifference() {
        return maxHeadingDifference;
    }

    public PriorityConnectedRsuList(VehicleConfig vehicleConfig, Float maxHeadingDifference){ //forma usada no veículo
        this.vehicleConfig = vehicleConfig;
        this.maxHeadingDifference = maxHeadingDifference;
    }
    public PriorityConnectedRsuList(Float maxHeadingDifference){ //forma usada no servidor
        this.maxHeadingDifference = maxHeadingDifference;
    }

    //Fazer método de update da lista com o cálculo da distância dos veículos.


    public LinkedList<RsuAnnouncedInfo> getRsuList(){
        return priorityRsuList;
    }

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
                rsu.setDistanceToVehicle(vehicleData.getPosition().getLatitude(),vehicleData.getPosition().getLongitude());
                rsu.setHeadingDiferenceToVehicle(vehicleData);
                relativeSpeed = calculateRelativeSpeed(rsu.getHeadingDiferenceToVehicle());
                timeToReachRsu = rsu.getDistanceToVehicle() / relativeSpeed;
                rsu.setTimeToReachRsu(timeToReachRsu); //set relative time to vehicle to reach this RSU

                if(Objects.equals(rsu.getRsuId(), announcedRsu.getRsuId())){ //Se o remetende do RSUBeacon já está na lista, atualizar o instante de chegada do Beacon, pois demais elementos já foram atualizados
                    rsu.setBeaconArrivedTime(announcedRsu.getBeaconArrivedTime());
                    rsuFound = true;
                }
                //se o RSU não envia beacons a mais de 5 segundos ou se o HeadingDifference está acima do máximo, remover o RSU da lista.
                if((rsu.getBeaconArrivedTime()+5*TIME.SECOND)<announcedRsu.getBeaconArrivedTime()
                        || rsu.getHeadingDiferenceToVehicle()>this.maxHeadingDifference
                        || rsu.getDistanceToVehicle()>vehicleConfig.radioRange)
                    {
                    priorityRsuList.remove(index);
                }else{
                    index++;
                }

            }


        }
        if(!rsuFound){ //Se o RSU anunciado não está na lista, adicionar. Não ver maxheading diference neste caso.
            announcedRsu.setDistanceToVehicle(vehicleData.getPosition().getLatitude(),vehicleData.getPosition().getLongitude());
            announcedRsu.setHeadingDiferenceToVehicle(vehicleData);
            relativeSpeed = calculateRelativeSpeed(announcedRsu.getHeadingDiferenceToVehicle());
            timeToReachRsu = announcedRsu.getDistanceToVehicle() / relativeSpeed;
            announcedRsu.setTimeToReachRsu(timeToReachRsu);
            this.priorityRsuList.addFirst(announcedRsu);
        }

        //Após o final a lista não estará ordenada. Deve-se chamar o método de ordenação
    }

    //método que atualiza as distâncias dos RSUs para os veículos.
    public void updateRsusData(VehicleData vehicleData, long simulationTime, PriorityConnectedRsuList rsusAMover ){
        double relativeSpeed;
        double timeToReachRsu;
        if(!this.priorityRsuList.isEmpty()){
            //Atualiza a distância do RSU com relação ao veículo
            RsuAnnouncedInfo rsu;
            int index = 0;
            //Atualiza os dados de cada um dos RSUs
            while (index < priorityRsuList.size()){
                rsu = priorityRsuList.get(index);
                rsu.setDistanceToVehicle(vehicleData.getPosition().getLatitude(),vehicleData.getPosition().getLongitude());
                rsu.setHeadingDiferenceToVehicle(vehicleData);
                relativeSpeed = calculateRelativeSpeed(rsu.getHeadingDiferenceToVehicle());
                timeToReachRsu = rsu.getDistanceToVehicle() / relativeSpeed;
                rsu.setTimeToReachRsu(timeToReachRsu); //set relative time to vehicle to reach this RSU


                //se o RSU não envia beacons a mais de 5 segundos ou se está fora do range, remover apenas. Se o HeadingDifference está acima do máximo e abaixo de 180, mover para a lista e movimentação.
                if((rsu.getBeaconArrivedTime()+5*TIME.SECOND)<simulationTime
                        || rsu.getHeadingDiferenceToVehicle()>this.maxHeadingDifference
                        || rsu.getDistanceToVehicle()>vehicleConfig.radioRange)//rsus velhos podem ter mais de 200m de distância
                {
                    if(rsu.getHeadingDiferenceToVehicle()>this.maxHeadingDifference && rsu.getHeadingDiferenceToVehicle()<180D){
                        //copiar para a lista de movimentação antes de excluir.
                        rsusAMover.insertRsuData(priorityRsuList.get(index),vehicleData);
                    }
                    priorityRsuList.remove(index);
                }else{
                    index++;
                }

            }


        }
        //Após o final a lista não estará ordenada. Deve-se chamar o método de ordenação
        sortRsuList();
    }

    private void sortRsuList(){
        if(priorityRsuList.size()>1){
                Collections.sort(priorityRsuList); //Ordena a Lista de Prioridades. Ver método compareTo da classe RsuAnnoucedInfo
                if(priorityRsuList.size()>vehicleConfig.maxRsuListSize) priorityRsuList.removeLast();
        }

    }

    public void insertRsuData(RsuAnnouncedInfo announcedRsu, VehicleData vehicleData){
        //Inserção Ordenada na lista de prioridade
        double relativeSpeed;
        double timeToReachRsu;
        announcedRsu.setDistanceToVehicle(vehicleData.getPosition().getLatitude(),vehicleData.getPosition().getLongitude());
        announcedRsu.setHeadingDiferenceToVehicle(vehicleData);
        relativeSpeed = calculateRelativeSpeed(announcedRsu.getHeadingDiferenceToVehicle());
        timeToReachRsu = announcedRsu.getDistanceToVehicle() / relativeSpeed;
        announcedRsu.setTimeToReachRsu(timeToReachRsu);

        if(this.priorityRsuList.isEmpty()){
            priorityRsuList.addFirst(announcedRsu);  //se estiver vazia adiciona no início
        }else{//Há elementos na lista
            int index = 0;
            while (index < priorityRsuList.size()){
                if(announcedRsu.getDistanceToVehicle()<=priorityRsuList.get(index).getDistanceToVehicle()){
                    //Se o o elemento a ser adicionado tem menor distância, adicionar no lugar do elemento
                    priorityRsuList.add(index,announcedRsu);
                    return;
                }
                index++;
            }
            this.priorityRsuList.addLast(announcedRsu); //se não está mais perto que nenhum, então adiciona no final
        }

    }

    private double calculateRelativeSpeed(double headingDiference) {
        // Assuming a simple linear relationship between heading difference and speed
        //
        return vehicleConfig.fixedVirtualSpeed-(vehicleConfig.headingDiferenceWeigth*headingDiference);
    }

}
