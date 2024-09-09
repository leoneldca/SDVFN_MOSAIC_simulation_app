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

import org.eclipse.mosaic.app.sdnvfn.config.RsuOFSwitchAppConfig;
import org.eclipse.mosaic.app.sdnvfn.message.*;
import org.eclipse.mosaic.app.sdnvfn.network.*;
import org.eclipse.mosaic.app.sdnvfn.utils.IntraUnitAppInteractor;
import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.CamBuilder;
import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.ReceivedAcknowledgement;
import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.ReceivedV2xMessage;
import org.eclipse.mosaic.fed.application.app.ConfigurableApplication;
import org.eclipse.mosaic.fed.application.app.api.CommunicationApplication;
import org.eclipse.mosaic.fed.application.app.api.os.RoadSideUnitOperatingSystem;
import org.eclipse.mosaic.interactions.communication.V2xMessageTransmission;
import org.eclipse.mosaic.lib.objects.v2x.etsi.Cam;
import org.eclipse.mosaic.lib.util.scheduling.Event;

import java.util.Objects;

/**
 * Aplicação que trabalha como um switch OpenFlow para o envio de dados para Servidor, Veículos ou RSUs
 */
public class RsuOFSwitchApp extends ConfigurableApplication<RsuOFSwitchAppConfig,RoadSideUnitOperatingSystem> implements CommunicationApplication {

    private RsuOFSwitchAppConfig rsuOFSwitchConfig;
    private IntraUnitAppInteractor applicationsInteractor;
    private FlowTable flowTable;
    private OFSwitchPorts ofSwitchPorts;
    private OFSwitchV2xPacketHandler v2xPacketHandler;
    private OFSwitchOFPacketHandler ofPacketHandler;

    public RsuOFSwitchApp() {
        super(RsuOFSwitchAppConfig.class, "RsuOFSwitchAppConfig");
    }

    /**
     *
     */
    @Override
    public void onStartup() {
        ///Instanciação do Application Interactor para Mensagens IntraUnit
        applicationsInteractor = new IntraUnitAppInteractor(this);
        ///Carrega configurações de RSU do arquivo RsuConfiguration.json
        this.rsuOFSwitchConfig = this.getConfiguration();  //load ConfigFile into config object
        //Criar abstração de portas do switch.
        this.ofSwitchPorts = new OFSwitchPorts(this,this.rsuOFSwitchConfig);  ///parametros: Operating System Access
        ///Instanciar o FlowTable para manipular os matching de fluxos - Construtor deve criar os fluxos base
        flowTable = new FlowTable(this, this.ofSwitchPorts,rsuOFSwitchConfig);
        v2xPacketHandler = new OFSwitchV2xPacketHandler(flowTable,this, this.ofSwitchPorts,rsuOFSwitchConfig);
        ofPacketHandler = new OFSwitchOFPacketHandler(flowTable,this, this.ofSwitchPorts,rsuOFSwitchConfig);
        //
    }

    private void packetReceiver(GenericV2xMessage v2xMessage){
        if(Objects.equals((v2xMessage).mappedV2xMsg.get("msgType"), this.rsuOFSwitchConfig.openFlowMsgType)){
            //Neste caso, chamar a função que trata das mensagens OpenFlow
            getLog().infoSimTime(this,"OpenFlow_MOD_Message: "+v2xMessage.toString());
            ofPacketHandler.ofPacketReceiver(v2xMessage);
            //getLog().infoSimTime(this,"FLOW_TABLE_POS_MOD"+this.flowTable.toString());
        }else{
            if(!Objects.equals(v2xMessage.getMsgType(), "rsuBeaconMsg"))
                getLog().infoSimTime(this,"V2X Message: "+v2xMessage.toString());
            v2xPacketHandler.packetMachingFunction(v2xMessage);
        }
    }



    /**
     * Recebimento de mensagens da rede na Aplicação de Rede / Switch
     * Ao receber uma mensagem de controle cujo o endereço for o servidor
     * @param receivedV2xMessage
     */
    @Override
    public void onMessageReceived(ReceivedV2xMessage receivedV2xMessage) {

        if(receivedV2xMessage.getMessage() instanceof GenericV2xMessage){
            GenericV2xMessage msg = (GenericV2xMessage)receivedV2xMessage.getMessage();
            if(Objects.equals(msg.getMsgType(), "rsuBeaconMsg")){
                return;//Não processa recebimento de beacons de outros RSUs
            }
            if(Objects.equals(msg.getMsgType(), "serviceResultMsg")){
                if(msg.getRouting().getDestination().getType().isAdHoc()){ //se o resultado é adhoc então deveria apenas esperar que veículos recebam
                    return;//Não processa recebimento de service result enviado para veículos
                }
                if(msg.getRouting().getDestination().getType().isCell() && !Objects.equals(msg.getRouting().getDestination().getAddress().getIPv4Address(), getOs().getAdHocModule().getSourceAddress().getIPv4Address())){ //se o resultado é adhoc
                    return;//Não processa recebimento de service result enviado para veículos
                }

            }

            this.packetReceiver((GenericV2xMessage) receivedV2xMessage.getMessage());
        }
        if(receivedV2xMessage.getMessage() instanceof Cam){
            Cam camMsg = (Cam)receivedV2xMessage.getMessage();
            applicationsInteractor.sendCamMsgToApp(camMsg, RsuKnowledgeBaseApp.class);

        }

    }



    /**
     * O processador de eventos do switch deve receber mensagens de aplicativos que desejam enviar mensagens pela rede.
     * A função repassa a mensagem para o recebedor de pacotes
     **/
    @Override
    public void processEvent(Event event) throws Exception {
        Object resource = event.getResource();

        if(resource instanceof GenericV2xMessage){
            GenericV2xMessage v2xMessage = (GenericV2xMessage) resource;
            ///Enviar mensagem para a PacketMatchingFunction
            this.packetReceiver(v2xMessage);
        }else{
            getLog().infoSimTime(this,"Switch não sabe como lidar com os dados redebidos");
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
        getLog().infoSimTime(this, "Finalizando Switch Application");
        getLog().infoSimTime(this,this.flowTable.toString());
    }





}
