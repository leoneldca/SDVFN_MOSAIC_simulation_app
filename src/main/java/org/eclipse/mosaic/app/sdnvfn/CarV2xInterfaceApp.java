/*
 * Copyright (c) 2020 Fraunhofer FOKUS and others. All rights reserved.
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contact: mosaic@fokus.fraunhofer.de
 */

package org.eclipse.mosaic.app.sdnvfn;

import org.eclipse.mosaic.app.sdnvfn.config.VehicleConfig;
import org.eclipse.mosaic.app.sdnvfn.information.PriorityConnectedRsuList;
import org.eclipse.mosaic.app.sdnvfn.information.RsuAnnouncedInfo;
import org.eclipse.mosaic.app.sdnvfn.message.GenericV2xMessage;
import org.eclipse.mosaic.app.sdnvfn.utils.IntraUnitAppInteractor;
import org.eclipse.mosaic.app.sdnvfn.utils.NetUtils;
import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.CamBuilder;
import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.ReceivedAcknowledgement;
import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.ReceivedV2xMessage;
import org.eclipse.mosaic.fed.application.app.ConfigurableApplication;
import org.eclipse.mosaic.fed.application.app.api.Application;
import org.eclipse.mosaic.fed.application.app.api.CommunicationApplication;
import org.eclipse.mosaic.fed.application.app.api.VehicleApplication;
import org.eclipse.mosaic.fed.application.app.api.os.VehicleOperatingSystem;
import org.eclipse.mosaic.interactions.communication.V2xMessageTransmission;
import org.eclipse.mosaic.lib.enums.AdHocChannel;
import org.eclipse.mosaic.lib.objects.v2x.etsi.Cam;
import org.eclipse.mosaic.lib.objects.vehicle.VehicleData;
import org.eclipse.mosaic.lib.util.scheduling.Event;

import java.awt.*;
import java.io.IOException;
import java.util.*;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Estabelece uma eleição do RSU que será o ponto de Acesso para a VFN (VFN_AP)
 * - Seleciona o RSU por meio da distância cartesiana.( Pode implementar outras formas de seleção)
 * - Recebe solicitação de envio de mensagens V2x vindas das camadas superiores
 * - Recebe as mensagens e encaminha para as mens
 * - envia mensagens utilizando as interfaces
 */
public class CarV2xInterfaceApp extends ConfigurableApplication<VehicleConfig,VehicleOperatingSystem> implements VehicleApplication, CommunicationApplication {

    private PriorityConnectedRsuList rsuPriorityList;
    private IntraUnitAppInteractor intraUnitAppInteractor;

    private String strRsuAccessPoint;
    private long lastReceivedBeaconTime;
    private VehicleConfig vehicleConfig;
    private RsuAnnouncedInfo rsuAP;
    private String strLastRsuAccessPoint;
    private List<PriorityConnectedRsuList> listOfRsuLists;
    private final Float MAX_DISTANCE_FACTOR = 0.85F;
    private final Float RANGE_FACTOR_TO_PREPARE_HANDOVER = 0.7F; //140 metros de distância
    private final Float MAX_HEADING_LIST1 = 45F;
    private final Float MAX_HEADING_LIST2 = 90F;
    private double lastDistanceDriven;

    public CarV2xInterfaceApp() {
        super(VehicleConfig.class, "VehicleConfiguration");
    }


    /**
     * We should enable ad hoc module here to be able to receive messages that were sent per ad hoc
     */
    @Override
    public void onStartup() {
        this.vehicleConfig = this.getConfiguration();  //load ConfigFile to config object
        lastDistanceDriven = 0D;
        intraUnitAppInteractor = new IntraUnitAppInteractor(this);
        this.strRsuAccessPoint = "null";
        this.rsuAP = null;
        this.strLastRsuAccessPoint = "null";
        this.lastReceivedBeaconTime = 0L;
        //rsuPriorityList = new PriorityConnectedRsuList(vehicleConfig,vehicleConfig.maxHeadingDifferenceList1);
        listOfRsuLists = new LinkedList<>();
        listOfRsuLists.add(0,new PriorityConnectedRsuList(vehicleConfig,vehicleConfig.maxHeadingDifferenceList1));
        listOfRsuLists.add(1,new PriorityConnectedRsuList(vehicleConfig,vehicleConfig.maxHeadingDifferenceList2));
        listOfRsuLists.add(2,new PriorityConnectedRsuList(vehicleConfig,180F));

        getLog().infoSimTime(this, "Setting up Vehicle");
        NetUtils.createAdHocInterface(this,this.vehicleConfig.radioRange,AdHocChannel.CCH); //cria uma interface adhoc para trabalhar no canal CCH
    }

    @Override
    public void onMessageReceived(ReceivedV2xMessage receivedV2xMessage) {


        if (receivedV2xMessage.getMessage() instanceof Cam) {
            try {
                Cam camMsg = (Cam)receivedV2xMessage.getMessage();
                getLog().infoSimTime(this, "CAM message arrived, userTaggedValue: {}",
                        CarCamSendingApp.DEFAULT_OBJECT_SERIALIZATION
                                .fromBytes(camMsg.getUserTaggedValue(), this.getClass().getClassLoader())
                );

                //VehicleAwarenessData awarenessData = (VehicleAwarenessData) camMsg.getAwarenessData();
            } catch (IOException | ClassNotFoundException e) {
                getLog().error("An error occurred", e);
            }

        }else{
            GenericV2xMessage msg = (GenericV2xMessage) receivedV2xMessage.getMessage();
            //msg.generateMappedMsg();
            if(Objects.equals(msg.getMsgType(), "rsuBeaconMsg")){

                getOs().requestVehicleParametersUpdate().changeColor(Color.ORANGE).apply();
                this.lastReceivedBeaconTime = getOs().getSimulationTime();


                //rsuPriorityList.updateRsuDistances(Objects.requireNonNull(getOs().getVehicleData()).getPosition().getLatitude(),getOs().getVehicleData().getPosition().getLongitude());

                RsuAnnouncedInfo rsuInfo = new RsuAnnouncedInfo(  //Announced RSU
                        msg.mappedV2xMsg.get("rsuId"),
                        Double.parseDouble(msg.mappedV2xMsg.get("latitude")),
                        Double.parseDouble(msg.mappedV2xMsg.get("longitude"))
                );
                rsuInfo.setBeaconArrivedTime(this.lastReceivedBeaconTime);
                rsuInfo.setDistanceToVehicle(getOs().getVehicleData().getPosition().getLatitude(), getOs().getVehicleData().getPosition().getLongitude());//Calcula a distância do veículo para o RSU remetente do beacon de RSU.
                rsuInfo.setHeadingDiferenceToVehicle(getOs().getVehicleData()); //Calcula a headingDiference entre o heading real do veículo com o heading necessário para ir ao encontro do RSU
                getLog().infoSimTime(this,"RSU_Beacon: RsuId:{}, Distance:{}, Heading:{}, ArrivedTime:{} ",rsuInfo.getRsuId(),rsuInfo.getDistanceToVehicle(),rsuInfo.getHeadingDiferenceToVehicle(),rsuInfo.getBeaconArrivedTime());
                if(rsuAP==null){
                    try {
                        rsuAP=(RsuAnnouncedInfo) rsuInfo.clone(); //Sem restrição quando o veículo estiver sem AP
                    } catch (CloneNotSupportedException e) {
                        throw new RuntimeException(e);
                    }
                }else{//Se não é nulo então deve ser atualizado, completamente se for o mesmo ou atualizado distâncias e heading se for diferente
                    if(Objects.equals(rsuAP.getRsuId(),rsuInfo.getRsuId())){
                        try {
                            rsuAP=(RsuAnnouncedInfo) rsuInfo.clone(); //Atualiza quando se trata do mesmo.
                        } catch (CloneNotSupportedException e) {
                            throw new RuntimeException(e);
                        }
                    }else{
                        rsuAP.setDistanceToVehicle(getOs().getVehicleData().getPosition().getLatitude(), getOs().getVehicleData().getPosition().getLongitude());//Calcula a distância do veículo para o RSU remetente do beacon de RSU.
                        rsuAP.setHeadingDiferenceToVehicle(getOs().getVehicleData()); //Calcula a headingDiference entre o heading real do veículo com o heading necessário para ir ao encontro do RSU
                    }

                }

                if(rsuInfo.getDistanceToVehicle() <= vehicleConfig.radioRange*MAX_DISTANCE_FACTOR){
                    if(rsuInfo.getHeadingDiferenceToVehicle()<= MAX_HEADING_LIST1){ //Até 45º inserir para a lista 1
                        listOfRsuLists.get(0).updatePriotyList(rsuInfo, getOs().getVehicleData(),vehicleConfig.radioRange*MAX_DISTANCE_FACTOR);
                    }else if(rsuInfo.getHeadingDiferenceToVehicle()<= MAX_HEADING_LIST2){ //Até 90º inserir para a lista 2
                        listOfRsuLists.get(1).updatePriotyList(rsuInfo, getOs().getVehicleData(),vehicleConfig.radioRange*MAX_DISTANCE_FACTOR);
                    }else{
                        listOfRsuLists.get(2).updatePriotyList(rsuInfo, getOs().getVehicleData(),vehicleConfig.radioRange*MAX_DISTANCE_FACTOR); //Acima de 90º, Inserir na lista 3
                    }
                }
                sortRsuLists(listOfRsuLists);
                this.strLastRsuAccessPoint = this.strRsuAccessPoint; //armazena o atual RsuAp como lastRsuAp
                selectRsuAP(listOfRsuLists); //seleciona o novo RSUAP, apenas se for necessário
                this.strRsuAccessPoint = this.rsuAP.getRsuId();
                if(!Objects.equals(this.strLastRsuAccessPoint,this.strRsuAccessPoint)){ //se for diferente, houve mudança de AP
                    getLog().infoSimTime(this,"new_RSU-AP: {}",this.strRsuAccessPoint);
                    this.sendElectedRsu(this.rsuAP);

                }else{//RSUAP continuará sendo o mesmo,
                    //getLog().infoSimTime(this,"Sem Mudança: {}",this.strRsuAccessPoint);
                }

            }else
            if(Objects.equals(msg.getMsgType(), this.vehicleConfig.serviceResultMsgType)){
                //getLog().infoSimTime(this,"Resultado recebido de : {} ",msg.getRouting().getSource().getSourceName());
                //getLog().info("Msg Id: {}",msg.mappedV2xMsg.get("msgId"));
                //getLog().info("Result: {}",msg.mappedV2xMsg.get("serviceProcessResult"));
                this.intraUnitAppInteractor.sendV2xMsgToApp(msg,CarService1ClientApp.class);

            }

        }


    }

    private void sortRsuLists(List<PriorityConnectedRsuList> listOfRsuLists){
        Float maxHeadingDifference = this.vehicleConfig.maxHeadingDifferenceList1;
        int index;
        for (PriorityConnectedRsuList priorityConnectedRsuList: listOfRsuLists){
            index = 0;
            if(priorityConnectedRsuList.getRsuList().size()>0){
                Collections.sort(priorityConnectedRsuList.getRsuList()); //Ordena a Lista de Prioridades. Ver método compareTo da classe RsuAnnoucedInfo
                if(priorityConnectedRsuList.getRsuList().size()>vehicleConfig.maxRsuListSize) priorityConnectedRsuList.getRsuList().removeLast();
            }
        }
    }

    private void selectRsuAP(List<PriorityConnectedRsuList> listOfRsuLists){
        if((listOfRsuLists.get(0).getRsuList().size()!=0
        ||listOfRsuLists.get(1).getRsuList().size()!=0
        ||listOfRsuLists.get(2).getRsuList().size()!=0)
        && (this.rsuAP.getDistanceToVehicle()>vehicleConfig.radioRange*MAX_DISTANCE_FACTOR)
        //&& (getOs().getVehicleData().getDistanceDriven()-lastDistanceDriven)>=20D //só espera os 20 metros iniciais
        && (this.rsuAP.getHeadingDiferenceToVehicle()>90D)){ //alguma lista deve não estar vazia, a distância não excedeu e veículo se deslocou+20metros realizar escolha ( que pode resultar no mesmo RSU_AP ou gerar a troca)

            if(listOfRsuLists.get(0).getRsuList().size()!=0){//se Lista 1 não está vazia, escolher da lista 1
                rsuAP=listOfRsuLists.get(0).getRsuList().getFirst();
            }else if(listOfRsuLists.get(1).getRsuList().size()!=0){//senão, se lista 2 não está vazia, escolher da lista 2
                rsuAP=listOfRsuLists.get(1).getRsuList().getFirst();
            }else if(listOfRsuLists.get(2).getRsuList().size()!=0){//senão, se lista 3 não está vazia, escolher da lista 3.
                rsuAP=listOfRsuLists.get(2).getRsuList().getFirst();
            }
            //lastDistanceDriven = getOs().getVehicleData().getDistanceDriven();//a proxima seleção deverá ser após
        }
    }

    /**
     * from EventProcessor interface
     **/
    @Override
    public void processEvent(Event event) throws Exception {
        Object resource = event.getResource();
        if(resource instanceof String){
            String msg = (String) resource;
            if(msg.equals("resetRsu")){
                //sempre que a aplicação da VFN parar de receber resposta ela assume que não há RSU conectada e solicita a confirmação de conexão, colocando a Flag como null.
                this.strRsuAccessPoint = "null";
                this.rsuAP = null;
                this.strLastRsuAccessPoint = "null";
                getOs().requestVehicleParametersUpdate().changeColor(Color.YELLOW).apply();
            }
        }
    }


    @Override
    public void onVehicleUpdated(@Nullable VehicleData previousVehicleData, @Nonnull VehicleData updatedVehicleData) {

        if(this.rsuAP!=null){
            this.rsuAP.setDistanceToVehicle(updatedVehicleData.getPosition().getLatitude(),updatedVehicleData.getPosition().getLongitude());
            if(this.rsuAP.getDistanceToVehicle()>(vehicleConfig.radioRange*RANGE_FACTOR_TO_PREPARE_HANDOVER)
                    && (this.rsuAP.getHeadingDiferenceToVehicle()>90D)){

                sendElectedRsu(rsuAP);
            }
        }
    }
    private void sendElectedRsu(RsuAnnouncedInfo rsuAnnouncedInfo){
        //Comunicando com outra aplicação via EventProcess
        List<? extends Application> appList = getOs().getApplications();
        for (Application app: appList) {
            if (app.getClass().getName()!=this.getClass().getName()){
                this.intraUnitAppInteractor.sendRsuAnnouncedInfoToApp(rsuAnnouncedInfo, app.getClass()); //envio de novo RSU-AP para as demais aplicações
            }
        }
    }


    @Override
    public void onAcknowledgementReceived(ReceivedAcknowledgement acknowledgedMessage) {
        //getLog().info(" acknowlegment Message receiced"); //Não funciona em rede AdHoc
    }

    @Override
    public void onCamBuilding(CamBuilder camBuilder) {
    }

    @Override
    public void onMessageTransmitted(V2xMessageTransmission v2xMessageTransmission) {
    }

    @Override
    public void onShutdown() {
        getLog().infoSimTime(this, "Ending Application");
    }

}
