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
import org.eclipse.mosaic.app.sdnvfn.message.RsuBeaconV2xMsg;
import org.eclipse.mosaic.app.sdnvfn.message.VfnServiceResultMsg;
import org.eclipse.mosaic.app.sdnvfn.utils.NetUtils;
import org.eclipse.mosaic.app.sdnvfn.utils.MsgUtils;
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
import org.eclipse.mosaic.lib.objects.v2x.V2xMessage;
import org.eclipse.mosaic.lib.objects.v2x.etsi.Cam;
import org.eclipse.mosaic.lib.objects.vehicle.VehicleData;
import org.eclipse.mosaic.lib.util.scheduling.Event;
import org.eclipse.mosaic.rti.TIME;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Application to receive message from adhoc network
 */
public class CarV2xReceiverApp extends ConfigurableApplication<VehicleConfig,VehicleOperatingSystem> implements VehicleApplication, CommunicationApplication {

    private final PriorityConnectedRsuList rsuPriorityList = new PriorityConnectedRsuList();

    private final HashMap<String,String> mappedV2xMsg = new HashMap<>();

    private List<? extends Application> applicationList;
    private HashMap<String,Application> appMap;

    private String rsuAccessPoint;
    private long lastReceivedBeaconTime;
    private VehicleConfig vhConfig;

    public CarV2xReceiverApp() {
        super(VehicleConfig.class, "VehicleConfiguration");
    }


    private void convertAppListToMap(){
        for (Application application : this.applicationList) {
            //Criar uma hash com as aplicações da Unidade
            //Inserir cada aplicação na Hash
            String[] strSplit = application.toString().split("@");
            this.appMap.put(strSplit[0],application);
        }
    }

    /**
     * We should enable ad hoc module here to be able to receive messages that were sent per ad hoc
     */
    @Override
    public void onStartup() {
        this.vhConfig = this.getConfiguration();  //load ConfigFile to config object
        this.applicationList = getOs().getApplications();
        this.appMap = new HashMap<>();
        this.convertAppListToMap();
        this.rsuAccessPoint = "null";
        this.lastReceivedBeaconTime = 0L;

        getLog().infoSimTime(this, "Setting up Vehicle");
        NetUtils.createAdHocInterface(this,this.vhConfig.radioRange,AdHocChannel.CCH); //cria uma interface adhoc para trabalhar no canal CCH
    }

    @Override
    public void onMessageReceived(ReceivedV2xMessage receivedV2xMessage) {
        V2xMessage msg = receivedV2xMessage.getMessage();

        if (msg instanceof Cam) {
            try {
                Cam camMsg = (Cam)msg;
                getLog().infoSimTime(this, "CAM message arrived, userTaggedValue: {}",
                        CarCamSendingApp.DEFAULT_OBJECT_SERIALIZATION
                                .fromBytes(camMsg.getUserTaggedValue(), this.getClass().getClassLoader())
                );

            } catch (IOException | ClassNotFoundException e) {
                getLog().error("An error occurred", e);
            }

        }else
        if(msg instanceof RsuBeaconV2xMsg){
            /*
            Ao receber uma mensagem de beacon, definir qual o RSU escolhido e atualizar a aplicação em caso de mudança.

             */
            this.mappedV2xMsg.clear();
            this.lastReceivedBeaconTime = getOs().getSimulationTime();
            getLog().infoSimTime(this, "-----\n\n\n{} msg from {}: {}", msg.getSimpleClassName(), msg.getRouting().getSource().getSourceName(),msg.toString());
            this.convertBeaconMsgToHash(msg.toString(),this.mappedV2xMsg);
            RsuAnnouncedInfo rsuInfo = new RsuAnnouncedInfo(
                    mappedV2xMsg.get("rsuId"),
                    Double.parseDouble(mappedV2xMsg.get("latitude")),
                    Double.parseDouble(mappedV2xMsg.get("longitude"))
            );
            getLog().info("Beacon recebido de RSU: {}",rsuInfo.getRsuId());
            //adicionar o RSU emissor da mensagem para a lista de RSUs
            rsuInfo.setDistanceToVehicle(getOs().getVehicleData().getPosition().getLatitude(), getOs().getVehicleData().getPosition().getLongitude());
            getLog().info(rsuInfo.getDistanceToVehicle().toString());
            rsuInfo.setBeaconArrivedTime(this.lastReceivedBeaconTime);
            mappedV2xMsg.clear();
            this.rsuPriorityList.updateRsuDistances(Objects.requireNonNull(getOs().getVehicleData()).getPosition().getLatitude(),getOs().getVehicleData().getPosition().getLatitude());
            rsuPriorityList.insertAnnouncedRsu(rsuInfo);
            getLog().infoSimTime(this,"List of near known RSU of vehicle {}:", getOs().getId());
            for (RsuAnnouncedInfo rsu : this.rsuPriorityList.getRsuList()) {
                getLog().info("RSU ID: {} at cartesian distance: {}",rsu.getRsuId(),rsu.getDistanceToVehicle());
            }
            getLog().info("--------------------------");

            if(!rsuPriorityList.getRsuList().isEmpty()){
                if(!Objects.equals(rsuPriorityList.getRsuList().getFirst().getRsuId(), this.rsuAccessPoint) || getOs().getSimulationTime()>(this.lastReceivedBeaconTime+3*TIME.SECOND) ){
                    this.rsuAccessPoint = rsuPriorityList.getRsuList().getFirst().getRsuId();
                    getLog().info("Novo RSU anunciado:{}",rsuPriorityList.getRsuList().getFirst().getRsuId());
                    getLog().info("Novo RSU AccessPoint: {}",this.rsuAccessPoint);
                    getLog().infoSimTime(this, "Nova atualização de RSU enviada para aplicações: {}",rsuPriorityList.getRsuList().getFirst().getRsuId());
                    getLog().info("\n\n---------------------------------------------");
                    this.sendElectedRsu(rsuPriorityList.getRsuList().getFirst());
                    //
                }

            }



        }else
        if(msg instanceof VfnServiceResultMsg){
            HashMap<String,String> mappedServiceRsultMsg = MsgUtils.extractMsgToHash(msg.toString());
            getLog().info("Resultado recebido: ");
            getLog().info("Msg Id: {}",mappedServiceRsultMsg.get("msgId"));
            getLog().info("Result: {}",mappedServiceRsultMsg.get("serviceProcessResult"));
            VfnServiceResultMsg vfnServiceResultMsg = (VfnServiceResultMsg) msg;
            this.dispachStrToApp(vfnServiceResultMsg,CarService1ClientApp.class);

        }
    }

    public void dispachStrToApp(VfnServiceResultMsg vfnServiceResultMsg, Class appClass){
        //Comunicando com outra aplicação via EventProcess
        final Event sendIntraUnitMsg = new Event(getOs().getSimulationTime(),this.appMap.get(appClass.getName()),vfnServiceResultMsg);
        this.getOs().getEventManager().addEvent(sendIntraUnitMsg);
    }

    public void convertBeaconMsgToHash(String strV2xMsg, HashMap<String,String> mappedV2xMsg){
        String[] strSplit = strV2xMsg.split("#");
        String coreMsg = strSplit[1];
        String[] strV2xSplited = coreMsg.split("=|;");
        for(int i=0;i<strV2xSplited.length-1;i=i+2){
            mappedV2xMsg.put(strV2xSplited[i],strV2xSplited[i+1]);
            //getLog().info("Key = {} ---> value= {}", strV2xSplited[i],strV2xSplited[i+1]);
        }
    }

    @Override
    public void onAcknowledgementReceived(ReceivedAcknowledgement acknowledgedMessage) {
        getLog().info(" acknowlegment Message receiced"); //Não funciona em rede AdHoc
    }

    @Override
    public void onCamBuilding(CamBuilder camBuilder) {
    }

    @Override
    public void onMessageTransmitted(V2xMessageTransmission v2xMessageTransmission) {
    }

    @Override
    public void onShutdown() {
        getLog().infoSimTime(this, "Tear down");
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
                this.rsuAccessPoint = "null";
            }
        }
    }


    @Override
    public void onVehicleUpdated(@Nullable VehicleData previousVehicleData, @Nonnull VehicleData updatedVehicleData) {
        this.rsuPriorityList.updateRsuDistances(Objects.requireNonNull(getOs().getVehicleData()).getPosition().getLatitude(),getOs().getVehicleData().getPosition().getLatitude());
    }
    private void sendElectedRsu(RsuAnnouncedInfo rsuAnnouncedInfo){
        //Comunicando com outra aplicação via EventProcess
        final List<? extends Application> applications = getOs().getApplications(); //Gerando lista de aplicações do Veículo
        for (Application application : applications) {
            final Event event = new Event(getOs().getSimulationTime(), application, rsuAnnouncedInfo);
            this.getOs().getEventManager().addEvent(event);
        }
    }

}
