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

import org.eclipse.mosaic.app.sdnvfn.CarCamSendingApp;
import org.eclipse.mosaic.app.sdnvfn.RsuKnowledgeBaseApp;
import org.eclipse.mosaic.app.sdnvfn.RsuToVfnMsgExchangeApp;
import org.eclipse.mosaic.app.sdnvfn.config.RsuConfig;
import org.eclipse.mosaic.app.sdnvfn.message.CarInfoToVfnMsg;
import org.eclipse.mosaic.app.sdnvfn.message.RsuBeaconV2xMsg;
import org.eclipse.mosaic.app.sdnvfn.message.VfnServiceMsg;
import org.eclipse.mosaic.app.sdnvfn.message.VfnServiceResultMsg;
import org.eclipse.mosaic.app.sdnvfn.network.CommunicationInterface;
import org.eclipse.mosaic.app.sdnvfn.utils.MsgUtils;
import org.eclipse.mosaic.app.sdnvfn.utils.IntraUnitAppInteractor;
import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.CamBuilder;
import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.ReceivedAcknowledgement;
import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.ReceivedV2xMessage;
import org.eclipse.mosaic.fed.application.app.ConfigurableApplication;
import org.eclipse.mosaic.fed.application.app.api.Application;
import org.eclipse.mosaic.fed.application.app.api.CommunicationApplication;
import org.eclipse.mosaic.fed.application.app.api.os.RoadSideUnitOperatingSystem;
import org.eclipse.mosaic.interactions.communication.V2xMessageTransmission;
import org.eclipse.mosaic.lib.enums.AdHocChannel;
import org.eclipse.mosaic.lib.objects.v2x.MessageRouting;
import org.eclipse.mosaic.lib.objects.v2x.V2xMessage;
import org.eclipse.mosaic.lib.objects.v2x.etsi.Cam;
import org.eclipse.mosaic.lib.util.scheduling.Event;
import org.eclipse.mosaic.rti.TIME;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;

/**
 * Aplicação de trabalha como um módulo de rede para o envio de dados
 */
public class RsuNetworkCommunicationApp extends ConfigurableApplication<RsuConfig,RoadSideUnitOperatingSystem> implements CommunicationApplication {

    private final static long BEACON_TIME_INTERVAL = TIME.SECOND;
    private List<? extends Application> applicationList;
    private HashMap<String,Application> appMap;
    private RsuConfig rsuConfig;
    private IntraUnitAppInteractor applicationsInteractor;

    private CommunicationInterface communicationInterface;

    public RsuNetworkCommunicationApp() {
        super(RsuConfig.class, "RsuConfiguration");
    }

    /**
     *
     */
    @Override
    public void onStartup() {
        applicationsInteractor = new IntraUnitAppInteractor(this); //objeto de interação entre aplications
        this.rsuConfig = this.getConfiguration();  //load ConfigFile into config object
        communicationInterface = new CommunicationInterface(this);
        getLog().infoSimTime(this, "Enabling Adhoc Module in {} ",getOs().getId());
        communicationInterface.createAdHocInterface(this.rsuConfig.radioRange,AdHocChannel.CCH);
        getLog().infoSimTime(this, "Enabling Cell Module in {} ",getOs().getId());
        communicationInterface.createCellInterface();


        this.applicationList = this.getOs().getApplications();
        this.appMap = new HashMap<>();
        this.convertAppListToMap();
        //Rsu beacon to adhoc network
        adhocBeaconScheduler();

    }
    public String getRsuStrData(){
        String rsuStrData;
        rsuStrData = "#rsuId="+getOs().getId()+
                ";latitude="+getOs().getPosition().getLatitude() +
                ";longitude="+getOs().getPosition().getLongitude()+
                "#";
        return rsuStrData;
    }
    public void adhocBeaconSender(){
        final MessageRouting routing =
                getOs().getAdHocModule().createMessageRouting().viaChannel(AdHocChannel.CCH).topoBroadCast(10);

        final RsuBeaconV2xMsg beaconV2xMsg = new RsuBeaconV2xMsg(routing,this.getRsuStrData());
        getOs().getAdHocModule().sendV2xMessage(beaconV2xMsg);
        //getLog().infoSimTime(this, "Adhoc beacon Sent by {} ",getOs().getId());
        adhocBeaconScheduler();

    }

    public void adhocBeaconScheduler(){
        Event event = new Event(getOs().getSimulationTime() + BEACON_TIME_INTERVAL, this,"rsuBeacon");
        getOs().getEventManager().addEvent(event);
    }


    /*
    O método dispachMsgToApp utiliza o agendador de eventos para enviar informações entre as aplicações.
     */
    public void dispachMsgToApp(@NotNull ReceivedV2xMessage receivedV2xMessage, Class appClass){
        //Comunicando com outra aplicação via EventProcess
        final Event sendIntraUnitMsg = new Event(getOs().getSimulationTime(),this.appMap.get(appClass.getName()),receivedV2xMessage);
        this.getOs().getEventManager().addEvent(sendIntraUnitMsg);
    }

    @Override
    public void onMessageReceived(ReceivedV2xMessage receivedV2xMessage) {
        V2xMessage msg = receivedV2xMessage.getMessage();
        if(!msg.getRouting().getSource().getSourceName().contains("veh_")) return; //somente trata mensagens vinda de veículos

        if (msg instanceof Cam) { try {
                byte [] camMsg = ((Cam)msg).getUserTaggedValue();
                getLog().infoSimTime(this, "CAM message arrived, userTaggedValue: {}",
                        CarCamSendingApp.DEFAULT_OBJECT_SERIALIZATION
                                .fromBytes(camMsg, this.getClass().getClassLoader())
                );
            } catch (IOException | ClassNotFoundException e) {
                getLog().error("An error occurred", e);
            }

        }else if (msg instanceof CarInfoToVfnMsg) {
                getLog().infoSimTime(this, "Adhoc message arrived:{}:",msg.toString());
                dispachMsgToApp(receivedV2xMessage, RsuToVfnMsgExchangeApp.class); //Envia mensagem para a Aplicação de transmissão para o Controlador Central
                dispachMsgToApp(receivedV2xMessage, RsuKnowledgeBaseApp.class); //Envia mensagem para a Aplicação controladora da RSU.
        }else if(msg instanceof VfnServiceMsg){
                getLog().infoSimTime(this,"Adhoc message arrived:{}:",msg.toString());
                dispachMsgToApp(receivedV2xMessage, RsuKnowledgeBaseApp.class); //Envia mensagem para a Aplicação controladora da RSU.
                //dispachMsgToApp(receivedV2xMessage, RsuToVfnMsgExchangeApp.class); //Envia mensagem para a Aplicação de transmissão para o Controlador Central
        }
    }

    @Override
    public void onAcknowledgementReceived(ReceivedAcknowledgement acknowledgedMessage) {

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

    private void adHocProcessResultMsgSender(HashMap<String,String> mappedResultMsg){
        String strResultMsg = MsgUtils.getMsgFromStringMap(mappedResultMsg);
        final MessageRouting msgRoute =
                getOs().getAdHocModule().createMessageRouting().viaChannel(AdHocChannel.CCH).
                        topoCast(mappedResultMsg.get("vhId"),5);

        final VfnServiceResultMsg serviceResultV2xMsg = new VfnServiceResultMsg(msgRoute,strResultMsg);
        getOs().getAdHocModule().sendV2xMessage(serviceResultV2xMsg);
    }
    /**
     * from EventProcessor interface
     **/
    @Override
    public void processEvent(Event event) throws Exception {
        Object resource = event.getResource();
        if(resource instanceof String){
            String strResource = (String) resource;
            if(strResource.equals("rsuBeacon")){
                this.adhocBeaconSender();
            }

        } else
        if(resource instanceof VfnServiceResultMsg){
            getLog().infoSimTime(this,"Returning process result to Vehicle: {}", resource.toString());
            //HashMap<String, String> mappedV2xMsg = Tools.extractMsgToHash(resource.toString());
            //adHocProcessResultMsgSender(mappedV2xMsg);
            getOs().getAdHocModule().sendV2xMessage((VfnServiceResultMsg)resource);


        }


    }

    private void convertAppListToMap(){
        for (Application application : this.applicationList) {
            //Criar uma hash com as aplicações da Unidade
            //Inserir cada aplicação na Hash
            String[] strSplit = application.toString().split("@");
            this.appMap.put(strSplit[0],application);
        }
        /*for(Map.Entry<String, Application> keyEntry : this.appMap.entrySet()){
            System.out.println(keyEntry.getKey()+"="+keyEntry.getValue());
        }

         */


    }
}
