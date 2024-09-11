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
import org.eclipse.mosaic.rti.TIME;

import java.awt.*;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
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

    private String rsuAccessPoint;
    private long lastReceivedBeaconTime;
    private VehicleConfig vehicleConfig;

    public CarV2xInterfaceApp() {
        super(VehicleConfig.class, "VehicleConfiguration");
    }


    /**
     * We should enable ad hoc module here to be able to receive messages that were sent per ad hoc
     */
    @Override
    public void onStartup() {
        this.vehicleConfig = this.getConfiguration();  //load ConfigFile to config object
        intraUnitAppInteractor = new IntraUnitAppInteractor(this);
        this.rsuAccessPoint = "null";
        this.lastReceivedBeaconTime = 0L;
        rsuPriorityList = new PriorityConnectedRsuList(vehicleConfig);

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
                //Ao receber uma mensagem de beacon, definir qual o RSU escolhido e atualizar a aplicação em caso de mudança.
                getOs().requestVehicleParametersUpdate().changeColor(Color.ORANGE).apply();
                this.lastReceivedBeaconTime = getOs().getSimulationTime();

                //atualiza as distâncias veículo para todos os RSUs da lista de prioridades, reordenando a lista
                //rsuPriorityList.updateRsuDistances(Objects.requireNonNull(getOs().getVehicleData()).getPosition().getLatitude(),getOs().getVehicleData().getPosition().getLongitude());

                //Cria um objeto da Classe RsuAnnoucedInfo para representar o rsu sendo anunciado
                RsuAnnouncedInfo rsuInfo = new RsuAnnouncedInfo(
                        msg.mappedV2xMsg.get("rsuId"),
                        Double.parseDouble(msg.mappedV2xMsg.get("latitude")),
                        Double.parseDouble(msg.mappedV2xMsg.get("longitude"))
                );
                rsuInfo.setBeaconArrivedTime(this.lastReceivedBeaconTime);
                //Calcula a distância do veículo para o RSU remetente do beacon de RSU.
                //rsuInfo.setDistanceToVehicle(getOs().getVehicleData().getPosition().getLatitude(), getOs().getVehicleData().getPosition().getLongitude());

                //Calcula a headingDiference entre o heading real do veículo com o heading necessário para ir ao encontro do RSU
                //rsuInfo.setHeadingDiferenceToVehicle(getOs().getVehicleData());
                //Insere os dados do RSU na lista de prioridades. A inserção reordena e remove o mais longe se ultrapassar 5 RSUs
                rsuPriorityList.updatePriotyList(rsuInfo, getOs().getVehicleData());
                //getLog().infoSimTime(this,"List of near known RSU of vehicle {}:", getOs().getId());
                /*for (RsuAnnouncedInfo rsu : this.rsuPriorityList.getRsuList()) {
                    getLog().info("RSU ID: {} at cartesian distance: {}",rsu.getRsuId(),rsu.getDistanceToVehicle());
                }*/

                if(!rsuPriorityList.getRsuList().isEmpty()){
                    //Critérios para trocar de RSU_AP
                    if(!Objects.equals(rsuPriorityList.getRsuList().getFirst().getRsuId(), this.rsuAccessPoint) || getOs().getSimulationTime()>(this.lastReceivedBeaconTime+3*TIME.SECOND) ){
                        this.rsuAccessPoint = rsuPriorityList.getRsuList().getFirst().getRsuId();
                        getLog().infoSimTime(this,"new_RSU-AP: {}",this.rsuAccessPoint);
                        //getLog().infoSimTime(this, "Nova atualização de RSU enviada para aplicações: {}",rsuPriorityList.getRsuList().getFirst().getRsuId());
                        //getLog().info("\n\n---------------------------------------------");
                        this.sendElectedRsu(rsuPriorityList.getRsuList().getFirst());
                        //
                    }

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
                getOs().requestVehicleParametersUpdate().changeColor(Color.YELLOW).apply();
            }
        }
    }


    @Override
    public void onVehicleUpdated(@Nullable VehicleData previousVehicleData, @Nonnull VehicleData updatedVehicleData) {
        //this.rsuPriorityList.updateRsuDistances(Objects.requireNonNull(getOs().getVehicleData()).getPosition().getLatitude(),getOs().getVehicleData().getPosition().getLongitude());
    }
    private void sendElectedRsu(RsuAnnouncedInfo rsuAnnouncedInfo){
        //Comunicando com outra aplicação via EventProcess
        List<? extends Application> appList = getOs().getApplications();
        for (Application app: appList) {
            if (app.getClass().getName()!=this.getClass().getName()){
                this.intraUnitAppInteractor.sendRsuAnnouncedInfoToApp(rsuAnnouncedInfo, app.getClass()); //envio de novo RSU-AP para as demais aplicações
            }
        }
        //this.intraUnitAppInteractor.sendRsuAnnouncedInfoToApp(rsuAnnouncedInfo, CarService1ClientApp.class);
        //this.intraUnitAppInteractor.sendRsuAnnouncedInfoToApp(rsuAnnouncedInfo, CarInfoToVfnSendingApp.class);
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
