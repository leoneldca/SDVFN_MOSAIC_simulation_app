package org.eclipse.mosaic.app.sdnvfn.network;

import org.eclipse.mosaic.app.sdnvfn.config.ServerConfig;
import org.eclipse.mosaic.app.sdnvfn.information.PriorityConnectedRsuList;
import org.eclipse.mosaic.app.sdnvfn.information.RsuAnnouncedInfo;
import org.eclipse.mosaic.app.sdnvfn.information.VfnConnectedVehicle;
import org.eclipse.mosaic.app.sdnvfn.utils.HeadingCalculator;
import org.eclipse.mosaic.app.sdnvfn.utils.NodesUtils;
import org.eclipse.mosaic.lib.geo.MutableGeoPoint;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

public class NextRsuSelector {
    private HashMap<String, MutableGeoPoint> rsuPositionMap;
    private VfnConnectedVehicle connectedVehicle;
    private ServerConfig serverConfig;

    private static final float HEADING_DIFFERENCE_WEIGHT = 1F;


    public NextRsuSelector(HashMap<String, MutableGeoPoint> rsuPositionMap, ServerConfig serverConfig) {
        this.rsuPositionMap = rsuPositionMap;
        this.serverConfig = serverConfig;
    }

    /**
     * Método realiza a seleção do próximo RSU-AP no qual o veículo irá se conectar.
     * @param connectedVehicle Recebe como parâmetro o objeto veículo
     * @return retorna um objeto String que contem o Predicted-Next-RSU-AP
     */
    public String selectNextRsuToVehicle(VfnConnectedVehicle connectedVehicle) {
        this.connectedVehicle = connectedVehicle;

        if(this.rsuPositionMap.size()<2 ){ //Seria o caso de um mapa com apenas 1 RSU, manter sempre o mesmo.
            return connectedVehicle.getRsuApId();
        }
        if(!(connectedVehicle.getActualDistanceToRsu()>=connectedVehicle.getLastDistanceToRsu()
                && (connectedVehicle.getActualDistanceToRsu()>= (serverConfig.adHocRadioRange *serverConfig.handoverPredictionZoneMultiplier)))) { //75% do range
            return connectedVehicle.getRsuApId(); ////Se ainda está se aproximando do RSU-AP atual, mantem. Se distancia é menor que 70% da máxima, mantém. Se os dois caso acontecem, mantem
        }//se está se distanciando e a distância é maior de 70% da máxima, então realizar a predição de outra RSU.
        double minTimeToReachRsu = Double.MAX_VALUE;
        String predictedRSUId = this.connectedVehicle.getRsuApId(); //Num primeiro momento assumir que o proximo RSU-AP será o atual. pois podem não haver outros RSUs.


        //limpar as listas de RSUs do veículo
        for(PriorityConnectedRsuList rsuList: connectedVehicle.getListOfRsuLists()){
            rsuList.getRsuList().clear();
        }
        //preencher as listas com RSUs que estão no range do veículo (pesquisar na lista geral)
        for (HashMap.Entry<String, MutableGeoPoint>  rsuFromBigList : this.rsuPositionMap.entrySet()) {//Percorre a BigList em busca de RSUs no Range do veículo.

            double distance = NodesUtils.calculateDistanceBetweenNodes(this.connectedVehicle.getLatitude(), this.connectedVehicle.getLongitude(),rsuFromBigList.getValue().getLatitude(), rsuFromBigList.getValue().getLongitude());
            if(distance<=serverConfig.adHocRadioRange*serverConfig.handoverZoneMultiplier){ //Se não está na zona de handover, então inserir na lista

                //todo RSU no range passa a ser um objeto que entrará em uma das 3 listas
                RsuAnnouncedInfo rsuInTheVehicleRange = new RsuAnnouncedInfo(  //Announced RSU
                        rsuFromBigList.getKey(),
                        rsuFromBigList.getValue().getLatitude(),
                        rsuFromBigList.getValue().getLongitude()
                );
                rsuInTheVehicleRange.setDistanceToVehicle(distance);
                rsuInTheVehicleRange.setHeadingDiferenceToVehicle(HeadingCalculator.calculateHeadingDifference(
                        this.connectedVehicle.getHeading(),
                        this.connectedVehicle.getLatitude(),
                        this.connectedVehicle.getLongitude(),
                        rsuFromBigList.getValue().getLatitude(),
                        rsuFromBigList.getValue().getLongitude()));

                //insere o RSU do range em uma das listas
                if(rsuInTheVehicleRange.getHeadingDiferenceToVehicle()<=serverConfig.maxHeadingDifferenceList1){ //Até 45º inserir para a lista 1
                    this.insertInRsuPriotyList(connectedVehicle.getListOfRsuLists().get(0), rsuInTheVehicleRange,connectedVehicle);
                }else if(rsuInTheVehicleRange.getHeadingDiferenceToVehicle()<= serverConfig.maxHeadingDifferenceList2){ //Até 90º inserir para a lista 2
                    this.insertInRsuPriotyList(connectedVehicle.getListOfRsuLists().get(1), rsuInTheVehicleRange,connectedVehicle);
                }else{
                    this.insertInRsuPriotyList(connectedVehicle.getListOfRsuLists().get(2), rsuInTheVehicleRange,connectedVehicle);
                }

                if(Objects.equals(connectedVehicle.getRsuApId(), rsuInTheVehicleRange.getRsuId()))  {//Se encontrou o RSU-AP atual
                    connectedVehicle.setActualRsuAp(rsuInTheVehicleRange); //troca a referência para este que já foi inserido e atualizado
                }

            }

        }
        //selecionar um rsu da lista

        predictedRSUId = selecBestRsuAP(connectedVehicle.getListOfRsuLists(),connectedVehicle); //retorna uma string do RSU Selecionado.
        return predictedRSUId; //connectedVehicle.getRsuApId(); // //connectedVehicle.getRsuApId(); //sem predição //
    }

    private double calculateRelativeSpeed(double headingDiference){
        // Assuming a simple linear relationship between heading difference and speed
        //
        return 180-(HEADING_DIFFERENCE_WEIGHT*headingDiference);
    }

    public void insertInRsuPriotyList(PriorityConnectedRsuList priorityRsuList, RsuAnnouncedInfo rsuInTheVehicleRange, VfnConnectedVehicle connectedVehicle){
        double relativeSpeed;
        double timeToReachRsu;

        //calcula as distâncias entre RSU e Veículo, assim como o headingDifference entre eles
        rsuInTheVehicleRange.setDistanceToVehicle(connectedVehicle.getLatitude(),connectedVehicle.getLongitude());
        rsuInTheVehicleRange.setHeadingDiferenceToVehicle(HeadingCalculator.calculateHeadingDifference(
                connectedVehicle.getHeading(),
                connectedVehicle.getLatitude(),
                connectedVehicle.getLongitude(),
                rsuInTheVehicleRange.getLatRsu(),
                rsuInTheVehicleRange.getLongRsu()
        ));

        relativeSpeed = calculateRelativeSpeed(rsuInTheVehicleRange.getHeadingDiferenceToVehicle());
        timeToReachRsu = rsuInTheVehicleRange.getDistanceToVehicle() / relativeSpeed;
        rsuInTheVehicleRange.setTimeToReachRsu(timeToReachRsu);

        if(priorityRsuList.getRsuList().isEmpty()){ //se lista vazia, insere na primeira posição
            priorityRsuList.getRsuList().addFirst(rsuInTheVehicleRange);
        }else{
            boolean inserted = false;
            int index = 0;
            while (index < priorityRsuList.getRsuList().size()){
                if(rsuInTheVehicleRange.getDistanceToVehicle()<priorityRsuList.getRsuList().get(index).getDistanceToVehicle()){
                    //se distância é menor que o elemento atual, insere ordenado
                    priorityRsuList.getRsuList().add(index,rsuInTheVehicleRange);
                    inserted = true;
                    break;
                }else{
                    index++; //se é maior, verifica se é maior que o próximo
                }
            }
            if(!inserted){
                //se não é achou posição em que o Rsu tenha menor distância, adiciona no final.
                priorityRsuList.getRsuList().addLast(rsuInTheVehicleRange);
            }
        }
    }

    private void sortRsuLists(List<PriorityConnectedRsuList> listOfRsuLists){
        for (PriorityConnectedRsuList priorityConnectedRsuList: listOfRsuLists){
            if(priorityConnectedRsuList.getRsuList().size()>0){
                Collections.sort(priorityConnectedRsuList.getRsuList()); //Ordena a Lista de Prioridades. Ver método compareTo da classe RsuAnnoucedInfo
                if(priorityConnectedRsuList.getRsuList().size()>serverConfig.maxRsuListSize) priorityConnectedRsuList.getRsuList().removeLast();
            }
        }
    }

    private String selecBestRsuAP(List<PriorityConnectedRsuList> listOfRsuLists, VfnConnectedVehicle connectedVehicle){
        String selectedRsu=connectedVehicle.getRsuApId();
        double vehicleRsuHeadingDifference = HeadingCalculator.calculateHeadingDifference(
                connectedVehicle.getHeading(),
                connectedVehicle.getLatitude(),
                connectedVehicle.getLongitude(),
                this.rsuPositionMap.get(connectedVehicle.getRsuApId()).getLatitude(),
                this.rsuPositionMap.get(connectedVehicle.getRsuApId()).getLongitude());
        if((listOfRsuLists.get(0).getRsuList().size()!=0
                ||listOfRsuLists.get(1).getRsuList().size()!=0
                ||listOfRsuLists.get(2).getRsuList().size()!=0)
                && (connectedVehicle.getActualDistanceToRsu()>serverConfig.adHocRadioRange*serverConfig.handoverPredictionZoneMultiplier)
                && (vehicleRsuHeadingDifference>serverConfig.maxHeadingDifferenceList2)){ //alguma lista deve não estar vazia, já está na zona de predição e já está a mais de 90 graus de headingDifference do RSU.

            if(listOfRsuLists.get(0).getRsuList().size()!=0){//se Lista 1 não está vazia, escolher da lista 1
                selectedRsu=listOfRsuLists.get(0).getRsuList().getFirst().getRsuId();
            }else if(listOfRsuLists.get(1).getRsuList().size()!=0){//senão, se lista 2 não está vazia, escolher da lista 2
                selectedRsu=listOfRsuLists.get(1).getRsuList().getFirst().getRsuId();
            }else if(listOfRsuLists.get(2).getRsuList().size()!=0){//senão, se lista 3 não está vazia, escolher da lista 3.
                selectedRsu=listOfRsuLists.get(2).getRsuList().getFirst().getRsuId();
            }

        }
        return selectedRsu; //retorna a referência para um RSU de uma das listas ou para o atual, caso não haja nenhum na lista
    }

}
