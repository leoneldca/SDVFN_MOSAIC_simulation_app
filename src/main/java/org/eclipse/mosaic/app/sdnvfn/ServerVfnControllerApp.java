package org.eclipse.mosaic.app.sdnvfn;

import org.eclipse.mosaic.app.sdnvfn.config.ServerConfig;
import org.eclipse.mosaic.app.sdnvfn.information.GlobalDynamicVehicleMap;
import org.eclipse.mosaic.app.sdnvfn.information.RsuConnectedVehicle;
import org.eclipse.mosaic.app.sdnvfn.message.CarInfoToVfnMsg;
import org.eclipse.mosaic.app.sdnvfn.message.ControllerServerMsg;
import org.eclipse.mosaic.app.sdnvfn.message.VfnServiceMsg;
import org.eclipse.mosaic.app.sdnvfn.utils.MsgUtils;
import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.CamBuilder;
import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.CellModuleConfiguration;
import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.ReceivedAcknowledgement;
import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.ReceivedV2xMessage;
import org.eclipse.mosaic.fed.application.app.ConfigurableApplication;
import org.eclipse.mosaic.fed.application.app.api.os.ServerOperatingSystem;
import org.eclipse.mosaic.fed.application.app.api.CommunicationApplication;
import org.eclipse.mosaic.interactions.communication.V2xMessageTransmission;
import org.eclipse.mosaic.lib.enums.ProtocolType;
import org.eclipse.mosaic.lib.objects.v2x.MessageRouting;
import org.eclipse.mosaic.lib.objects.v2x.V2xMessage;
import org.eclipse.mosaic.lib.util.scheduling.Event;
import org.eclipse.mosaic.rti.DATA;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class ServerVfnControllerApp extends ConfigurableApplication<ServerConfig, ServerOperatingSystem> implements CommunicationApplication {

    GlobalDynamicVehicleMap globalDynamicMap;

    public ServerVfnControllerApp() {
        super(ServerConfig.class, "ServerConfiguration");
    }


    @Override
    public void onStartup() {
        ServerConfig srvConfig = this.getConfiguration();  //load ConfigFile to config object
        getLog().info("Common ServiceList:{}", srvConfig.commonServiceList);
        getLog().info("Specific ServiceList:{}", srvConfig.specificServiceList);


        getLog().infoSimTime(this, "Initialize ServerVfnControllerApp application");
        getOs().getCellModule().enable(new CellModuleConfiguration()
                .maxDownlinkBitrate(50 * DATA.MEGABIT)
                .maxUplinkBitrate(50 * DATA.MEGABIT));
        getLog().infoSimTime(this, "Setup Controller server {} at time {}", getOs().getId(), getOs().getSimulationTime());
        getLog().infoSimTime(this, "Activated Cell Module");

        //The Global Dynamic Map extends generic DynamicMap. It needs the commonServiceList and the specificServiceList.
        globalDynamicMap = new GlobalDynamicVehicleMap(srvConfig.commonServiceList, srvConfig.specificServiceList);

    }


    @Override
    public void processEvent(Event event) throws Exception {
        //event to process
    }

    @Override
    public void onShutdown() {
        getLog().infoSimTime(this, "Shutdown VFN Controller Server App");
    }


    @Override
    public void onMessageReceived(ReceivedV2xMessage receivedV2xMessage) {
        getLog().infoSimTime(this, "Received Message: {}",
                receivedV2xMessage.getMessage().toString());

        //Ao receber uma informação (Beacom de Veículos)
        V2xMessage fullMsg = receivedV2xMessage.getMessage();
        if( fullMsg instanceof CarInfoToVfnMsg || fullMsg instanceof VfnServiceMsg){
            getLog().info("Received msg forwarded by {}",receivedV2xMessage.getMessage().getRouting().getSource().getSourceName());
            //It structs the message in a HashMap.
            HashMap<String, String> mappedMsg = MsgUtils.extractMsgToHash(receivedV2xMessage.getMessage().toString());
            mappedMsg.put("rsuId",receivedV2xMessage.getMessage().getRouting().getSource().getSourceName());
            this.globalDynamicMap.addVehicleInfo(mappedMsg);
            HashMap<String, RsuConnectedVehicle> mappedVehicles = this.globalDynamicMap.getVehiclesMap();

            if(fullMsg instanceof VfnServiceMsg){
               this.servicesMng(mappedMsg); // repassa a mensagem mapeada para a gestão dos servicos
                getLog().info("---------------------------MappedMsg-ServiceRunnerMsg---------------------");
                for(Map.Entry<String, String> keyEntry : mappedMsg.entrySet()){
                    getLog().info("{} = {}",keyEntry.getKey(), keyEntry.getValue());
                }
            }


            //Writing the vehicle list in a Log
            for (String key: mappedVehicles.keySet()){
                RsuConnectedVehicle vehicleInfo = mappedVehicles.get(key);
                getLog().infoSimTime(this,key+" = " +vehicleInfo.toString());
            }
            getLog().info("----------------------------------------------\n\n\n");

        }
    }

    /*
    Este método objetiva realizar a tomada de decisão sobre a execução de Serviços para cada veículo.
     */
    public void servicesMng(HashMap<String, String> mappedMsg){
        HashMap<String, RsuConnectedVehicle> mappedVehicles = this.globalDynamicMap.getVehiclesMap(); //referência para a Map dos veículos
        RsuConnectedVehicle vehicleData = mappedVehicles.get(mappedMsg.get("vhId")); //referência para o veículo que enviou a mensagem se serviço
        String rsuOfService = vehicleData.getRsuOfservice(mappedMsg.get("serviceId")); //Identifica a RSU do Serviço solicitado

        //Se não há RSU executor para o serviço, elege o RSU que encaminhou a mensagem.
        if(Objects.equals(rsuOfService, "unused")){
            vehicleData.setRsuServiceRunner(mappedMsg.get("serviceId"),mappedMsg.get("rsuId"));
        }else{
            //Se já existia um RSU executando, executa a lógica de alteração de RSU
            //Criar lógica de alterações
        }

        getLog().info("-------------------------------------------------");
        //propaga a decisão para o RSU que encaminhou a mensagem e para mais algum, a depender da lógica
        this.sendRsuServiceRunnerDecision(mappedMsg.get("rsuId"),
                mappedMsg.get("vhId"),
                mappedMsg.get("serviceId"),
                vehicleData.getRsuOfservice(mappedMsg.get("serviceId")));

    }

    public void sendRsuServiceRunnerDecision (String rsuForwarder, String vehicleId, String serviceId, String runnerRsuId){

        final MessageRouting routing = getOs().getCellModule().createMessageRouting().protocol(ProtocolType.TCP).topoCast(rsuForwarder);
        String coreMsg = "#vhId="+vehicleId+
                ";serviceId="+serviceId+
                ";rsuServiceRunner="+runnerRsuId+
                "#";

        getOs().getCellModule().sendV2xMessage(new ControllerServerMsg(routing, coreMsg));
        //criar tipo de mensagem que solicita a atualização de RSU de execução.

    }


    @Override
    public void onAcknowledgementReceived(ReceivedAcknowledgement receivedAcknowledgement) {

    }

    @Override
    public void onCamBuilding(CamBuilder camBuilder) {

    }

    @Override
    public void onMessageTransmitted(V2xMessageTransmission v2xMessageTransmission) {

    }


}
