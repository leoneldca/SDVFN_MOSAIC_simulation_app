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
import org.eclipse.mosaic.lib.objects.v2x.V2xReceiverInformation;
import org.eclipse.mosaic.lib.objects.v2x.etsi.Cam;
import org.eclipse.mosaic.lib.objects.vehicle.VehicleData;
import org.eclipse.mosaic.lib.util.scheduling.Event;
import org.eclipse.mosaic.rti.TIME;

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

    private IntraUnitAppInteractor intraUnitAppInteractor;
    private String strRsuAccessPoint;
    private long lastReceivedBeaconTime;
    private VehicleConfig vehicleConfig;
    private RsuAnnouncedInfo rsuAP;
    private String strLastRsuAccessPoint;
    private List<PriorityConnectedRsuList> listOfRsuLists;
    private double lastDistanceDriven;
    private final float MAX_HEADING_DIFFERENCE=180F;
    private HashMap<String,RsuAnnouncedInfo> mapRsuInRange;
    private boolean inHandoverProcess;
    private boolean handoverScheduled;

    public CarV2xInterfaceApp() {
        super(VehicleConfig.class, "VehicleConfiguration");
    }


    /**
     * We should enable ad hoc module here to be able to receive messages sent via ad hoc connection
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
        listOfRsuLists = new LinkedList<>();
        listOfRsuLists.add(0,new PriorityConnectedRsuList(vehicleConfig,vehicleConfig.maxHeadingDifferenceList1));
        listOfRsuLists.add(1,new PriorityConnectedRsuList(vehicleConfig,vehicleConfig.maxHeadingDifferenceList2));
        listOfRsuLists.add(2,new PriorityConnectedRsuList(vehicleConfig,MAX_HEADING_DIFFERENCE)); //MAX_HEADING_DIFFERENCE=180º
        mapRsuInRange = new HashMap<>();
        this.inHandoverProcess = false;
        this.handoverScheduled = false;

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
            if(msg.getMsgType().equals("rsuBeaconMsg") && !this.inHandoverProcess){ //Se está em processo de handover, não recebe beacons para não deturpar a escolha do proximo RSU-AP
                getOs().requestVehicleParametersUpdate().changeColor(Color.ORANGE).apply();
                this.lastReceivedBeaconTime = getOs().getSimulationTime(); //anota o momento da chegada do beacon de RSU.

                //instancia um objeto para as informações que chegaram via beacon.
                RsuAnnouncedInfo rsuInfo = new RsuAnnouncedInfo(  //Announced RSU
                        msg.mappedV2xMsg.get("rsuId"),
                        Double.parseDouble(msg.mappedV2xMsg.get("latitude")),
                        Double.parseDouble(msg.mappedV2xMsg.get("longitude"))
                );
                rsuInfo.setBeaconArrivedTime(this.lastReceivedBeaconTime); //armazena o momento de chegada do beacon
                rsuInfo.setDistanceToVehicle(getOs().getPosition().getLatitude(), getOs().getPosition().getLongitude());//Calcula a distância do veículo para o RSU remetente do beacon de RSU.
                rsuInfo.setHeadingDiferenceToVehicle(getOs().getVehicleData()); //Calcula a headingDiference entre o heading real do veículo com o heading necessário para ir ao encontro do RSU

                if(this.rsuAP==null){ //Caso o RSU-AP ainda não tenha sido definido. RSU-AP para a ser este mesmo da mensagem
                    try {
                        //this.inHandoverProcess=true;
                        this.rsuAP=(RsuAnnouncedInfo) rsuInfo.clone(); //Sem restrição quando o veículo estiver sem AP
                        //this.sendElectedRsu(this.rsuAP);
                        this.strRsuAccessPoint = this.rsuAP.getRsuId();
                        getLog().infoSimTime(this,"\nnew_RSU-AP: {} - After Lost Connection",this.strRsuAccessPoint);
                        this.sendElectedRsu(this.rsuAP); //após a primeira conexão, envia mensagem contendo o novo RSU selecionado
                        //this.inHandoverProcess =false; //finaliza qualquer processo de handover por se conectar em novo RSU.


                    } catch (CloneNotSupportedException e) {
                        throw new RuntimeException(e);
                    }
                }else{//Se o Beacon recebido vem do Próprio RSU-AP atual, atualizar suas distâncias com relação ao veículo.
                    if(rsuAP.getRsuId().equals(rsuInfo.getRsuId())){
                        try { //
                            rsuAP=(RsuAnnouncedInfo) rsuInfo.clone(); //Atualiza completamente quando se trata do mesmo RSU anunciado
                        } catch (CloneNotSupportedException e) {
                            throw new RuntimeException(e);
                        }
                    }else{ //atualizado apenas distância e headingDifference se o RSU anunciado não for o RSU-AP Atual.
                        rsuAP.setDistanceToVehicle(getOs().getVehicleData().getPosition().getLatitude(), getOs().getVehicleData().getPosition().getLongitude());//Calcula a distância do veículo para o RSU remetente do beacon de RSU.
                        rsuAP.setHeadingDiferenceToVehicle(getOs().getVehicleData()); //Calcula a headingDiference entre o heading real do veículo com o heading necessário para ir ao encontro do RSU
                    }

                }
                getLog().infoSimTime(this,"RSU_Beacon: RsuId:{}, Distance:{}, HeadingDifference:{}, ArrivedTime:{} ",rsuInfo.getRsuId(),rsuInfo.getDistanceToVehicle(),rsuInfo.getHeadingDiferenceToVehicle(),rsuInfo.getBeaconArrivedTime());
                updateLists(listOfRsuLists,rsuInfo); //
                //O recebimento de Beacons serve para atualizar a lista de RSUs no range do veículo. Apenas haverá seleção de RSU, se não houver nenhum rsu na lista


            }else
            if(Objects.equals(msg.getMsgType(), this.vehicleConfig.serviceResultMsgType)){
                //getLog().infoSimTime(this,"Resultado recebido de : {} ",msg.getRouting().getSource().getSourceName());
                //getLog().info("Msg Id: {}",msg.mappedV2xMsg.get("msgId"));
                //getLog().info("Result: {}",msg.mappedV2xMsg.get("serviceProcessResult"));
                this.intraUnitAppInteractor.sendV2xMsgToApp(msg,CarService1ClientApp.class);
            }
        }
    }

    private void updateLists(List<PriorityConnectedRsuList> listOfRsuLists, RsuAnnouncedInfo rsuInfo){
        //invoca a atualização de cada uma das listas e já reordena-as.
        mapRsuInRange.clear();
        for (PriorityConnectedRsuList rsuList: listOfRsuLists) {
            //rsuList.updateRsusData(getOs().getVehicleData(),getOs().getSimulationTime(),rsusAMover);
            int index =0;
            while (index < rsuList.getRsuList().size()){
                mapRsuInRange.put(rsuList.getRsuList().get(index).getRsuId(),rsuList.getRsuList().get(index));
                index++;
            }
            rsuList.getRsuList().clear();
        }
        mapRsuInRange.put(rsuInfo.getRsuId(),rsuInfo);//adiciona/replace no map os dados vindos via rsuInfo
        RsuAnnouncedInfo rsu;
        for (Map.Entry<String, RsuAnnouncedInfo> entry : mapRsuInRange.entrySet()) {
            rsu = entry.getValue();
            rsu.setDistanceToVehicle(getOs().getPosition().getLatitude(),getOs().getPosition().getLongitude());
            rsu.setHeadingDiferenceToVehicle(getOs().getVehicleData());
            if(rsu.getDistanceToVehicle()<vehicleConfig.radioRange){ //garante que RSUs fora do range não serão mantidos na lista.
                insereRsuNaLista(rsu);
            }


        }
        mapRsuInRange.clear();


    }



    private void insereRsuNaLista(RsuAnnouncedInfo rsuInfo){
            if(rsuInfo.getHeadingDiferenceToVehicle()<= vehicleConfig.maxHeadingDifferenceList1){ //Até 45º inserir para a lista 1
                listOfRsuLists.get(0).insertRsuData(rsuInfo, getOs().getVehicleData());
            }else if(rsuInfo.getHeadingDiferenceToVehicle()<= vehicleConfig.maxHeadingDifferenceList2){ //Até 90º inserir para a lista 2
                listOfRsuLists.get(1).insertRsuData(rsuInfo, getOs().getVehicleData());
            }else{
                listOfRsuLists.get(2).insertRsuData(rsuInfo, getOs().getVehicleData());
            }
    }

    private void selectRsuAP(List<PriorityConnectedRsuList> listOfRsuLists){
        if((listOfRsuLists.get(0).getRsuList().size()!=0
        ||listOfRsuLists.get(1).getRsuList().size()!=0
        ||listOfRsuLists.get(2).getRsuList().size()!=0)
        && (this.rsuAP.getDistanceToVehicle()>vehicleConfig.radioRange*vehicleConfig.handoverZoneMultiplier)
        && (this.rsuAP.getHeadingDiferenceToVehicle()>vehicleConfig.maxHeadingDifferenceList2)){ //alguma lista deve não estar vazia, a distância não excedeu e veículo se deslocou+20metros realizar escolha ( que pode resultar no mesmo RSU_AP ou gerar a troca)

            if(listOfRsuLists.get(0).getRsuList().size()!=0){//se Lista 1 não está vazia, escolher da lista 1
                rsuAP=listOfRsuLists.get(0).getRsuList().getFirst();
            }else if(listOfRsuLists.get(1).getRsuList().size()!=0){//senão, se lista 2 não está vazia, escolher da lista 2
                rsuAP=listOfRsuLists.get(1).getRsuList().getFirst();
            }else if(listOfRsuLists.get(2).getRsuList().size()!=0){//senão, se lista 3 não está vazia, escolher da lista 3.
                rsuAP=listOfRsuLists.get(2).getRsuList().getFirst();
            }
        }
    }

    private String selectNextRsuAP(List<PriorityConnectedRsuList> listOfRsuLists){
        if((listOfRsuLists.get(0).getRsuList().size()!=0
                ||listOfRsuLists.get(1).getRsuList().size()!=0
                ||listOfRsuLists.get(2).getRsuList().size()!=0)
                && (this.rsuAP.getDistanceToVehicle()>vehicleConfig.radioRange*vehicleConfig.handoverZoneMultiplier)
                //&& (getOs().getVehicleData().getDistanceDriven()-lastDistanceDriven)>=20D //só espera os 20 metros iniciais
                && (this.rsuAP.getHeadingDiferenceToVehicle()>vehicleConfig.maxHeadingDifferenceList2)){
            //alguma lista deve não estar vazia, a distância não excedeu a zona de handover ( que pode resultar no mesmo RSU_AP ou gerar a troca)

            if(listOfRsuLists.get(0).getRsuList().size()!=0){//se Lista 1 não está vazia, escolher da lista 1
                return listOfRsuLists.get(0).getRsuList().getFirst().getRsuId();
            }else if(listOfRsuLists.get(1).getRsuList().size()!=0){//senão, se lista 2 não está vazia, escolher da lista 2
                return listOfRsuLists.get(1).getRsuList().getFirst().getRsuId();
            }else if(listOfRsuLists.get(2).getRsuList().size()!=0){//senão, se lista 3 não está vazia, escolher da lista 3.
                return listOfRsuLists.get(2).getRsuList().getFirst().getRsuId();
            }
            //lastDistanceDriven = getOs().getVehicleData().getDistanceDriven();//a proxima seleção deverá ser após
        }
        return rsuAP.getRsuId();
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
            }else if(msg.equals("sendRsuApSelection") && this.rsuAP!=null){
                //realiza o processo de migração de AP após comunicação ao servidor via Método
                this.strLastRsuAccessPoint = this.strRsuAccessPoint; //armazena o atual RsuAp como lastRsuAp, pois se houver alteração na seleção
                selectRsuAP(this.listOfRsuLists); //A seleção pode gerar migração ou não. Seleção só trará resultados diferentes se o veículo estiver se movimentado demais
                this.strRsuAccessPoint = this.rsuAP.getRsuId(); //o resultado da seleção é armazenado no strRsuAccessPoint
                if(!Objects.equals(this.strLastRsuAccessPoint,this.strRsuAccessPoint)){ //se for diferente, houve mudança de AP
                    getLog().infoSimTime(this,"\nnew_RSU-AP: {}",this.strRsuAccessPoint);
                    this.sendElectedRsu(this.rsuAP); //após a migração, envia mensagem contendo o novo RSU selecionado
                    this.inHandoverProcess = false; //Finaliza o processo de handover, liberando para próximos.
                    this.handoverScheduled = false; //libera para novas migrações.
                }
            }
        }
    }

    //Teste com aviso único ao invés de vários avisos de zona de predição
    //se o next for diferente do


    @Override
    public void onVehicleUpdated(@Nullable VehicleData previousVehicleData, @Nonnull VehicleData updatedVehicleData) {
        boolean roadMinimalDistance = ((updatedVehicleData.getDistanceDriven()-this.lastDistanceDriven) > 1D);
        if(roadMinimalDistance){ //somente envia dados após 10 metros de percurso.
            if(this.rsuAP!=null){
                this.rsuAP.setDistanceToVehicle(updatedVehicleData.getPosition().getLatitude(),updatedVehicleData.getPosition().getLongitude());
                if(this.rsuAP.getDistanceToVehicle()>(vehicleConfig.radioRange*vehicleConfig.handoverZoneMultiplier) //entrou na zona de handover
                        && (this.rsuAP.getHeadingDiferenceToVehicle()>this.vehicleConfig.maxHeadingDifferenceList2)){ //está em uma posição atrás do veículo
                    //adicionar lógica para fazer isso apenas 1 vez.Se já tiver feito, não selecionar novamente
                    if(!this.inHandoverProcess){
                        updateLists(this.listOfRsuLists,rsuAP); //Primeiro passo do processo de Handover: Atualizar a lista de RSUs dentro do Range de sinal
                        rsuAP.setNextRsuId(selectNextRsuAP(listOfRsuLists)); //Identifica qual será o próximo RSU-AP do veículo, dentre aqueles da lista
                        if(!Objects.equals(rsuAP.getNextRsuId(), rsuAP.getRsuId())){ //Se identificou novo RSU-AP para o Veículo, Notificar Servidor.
                            sendElectedRsu(rsuAP); //envio de dados(Aplicações veículo e para Servidor), ainda sem migração, mas com a informação de próximo AP, atualizada.
                            getLog().infoSimTime(this,"\nPreparing_Handover_from_RSU-AP: {} to RSU-AP: {}",this.strRsuAccessPoint,rsuAP.getNextRsuId());
                            this.inHandoverProcess = true; //Indica que será iniciado o processo de handover
                        }
                    }
                    if(this.inHandoverProcess && !this.handoverScheduled){
                        this.scheduleRsuApSelectionMsgSending(); //criate a schedule to migrate to the predicted RSU-AP
                        this.handoverScheduled = true;
                    }
                    //this.inHandoverProcess é igual a true. Ou seja O processo de handover foi iniciado



                }
            }
            this.lastDistanceDriven = updatedVehicleData.getDistanceDriven();
        }


    }
    public void scheduleRsuApSelectionMsgSending() {
        //cria evento para envio de mensagem após 2 segundos
        Event event = new Event(getOs().getSimulationTime()+ TIME.SECOND, this,"sendRsuApSelection");
        getOs().getEventManager().addEvent(event);
    }

    private void sendElectedRsu(RsuAnnouncedInfo rsuAnnouncedInfo){
        //Comunicando com outra aplicação via EventProcess
        List<? extends Application> appList = getOs().getApplications();
        for (Application app: appList) {
            if (!app.getClass().getName().equals(this.getClass().getName())){
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
