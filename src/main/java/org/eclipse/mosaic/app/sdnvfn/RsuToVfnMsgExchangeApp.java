package org.eclipse.mosaic.app.sdnvfn;

import org.eclipse.mosaic.app.sdnvfn.message.*;
import org.eclipse.mosaic.app.sdnvfn.utils.MsgUtils;
import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.CamBuilder;
import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.CellModuleConfiguration;
import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.ReceivedAcknowledgement;
import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.ReceivedV2xMessage;
import org.eclipse.mosaic.fed.application.app.AbstractApplication;
import org.eclipse.mosaic.fed.application.app.api.Application;
import org.eclipse.mosaic.fed.application.app.api.CommunicationApplication;
import org.eclipse.mosaic.fed.application.app.api.os.RoadSideUnitOperatingSystem;
import org.eclipse.mosaic.interactions.communication.V2xMessageTransmission;
import org.eclipse.mosaic.lib.enums.AdHocChannel;
import org.eclipse.mosaic.lib.enums.ProtocolType;
import org.eclipse.mosaic.lib.objects.v2x.MessageRouting;
import org.eclipse.mosaic.lib.objects.v2x.V2xMessage;
import org.eclipse.mosaic.lib.util.scheduling.Event;
import org.eclipse.mosaic.rti.DATA;

import java.util.HashMap;
import java.util.List;

public class RsuToVfnMsgExchangeApp extends AbstractApplication<RoadSideUnitOperatingSystem> implements CommunicationApplication {

    private List<? extends Application> applicationList;
    private HashMap<String,Application> appMap;
    private String serverName = "server_0";
    private Integer msgCounter;


    @Override
    public void onStartup() {
        getLog().infoSimTime(this, "Enabling RsuToVfnMsgExchangeApp in {} ",getOs().getId());
        getOs().getCellModule().enable(new CellModuleConfiguration()
                .maxDownlinkBitrate(50 * DATA.MEGABIT)
                .maxUplinkBitrate(50 * DATA.MEGABIT));
        getLog().infoSimTime(this, "Activated Cell Module");

        this.applicationList = getOs().getApplications();
        this.appMap = new HashMap<>();
        this.convertAppListToMap();
        this.msgCounter=0;


    }

    protected String extractCoreMsg(V2xMessage v2xMsg){
        String[] strSplit = v2xMsg.toString().split("#");
        return "#"+strSplit[1]+"#";
    }



    @Override
    public void processEvent(Event event) throws Exception {
        Object resource = event.getResource();
        if (resource != null) {
            if(resource instanceof ForwardedServiceMsg){
                //considerar remoção. não ocorrerá
                ForwardedServiceMsg fwMessage = (ForwardedServiceMsg) resource;
                getLog().info("Mensagem pronta para ser encaminhada: {}", fwMessage.toString());
                getOs().getCellModule().sendV2xMessage(fwMessage);
            }else

            if(resource instanceof VfnServiceMsg){
                VfnServiceMsg serviceMsg = (VfnServiceMsg) resource;
                HashMap<String, String> mappedServiceMsg = MsgUtils.extractMsgToHash(serviceMsg.getCoreMsg());
                //a mensagem que chega aqui pode ser de solicitação de RsuRunner ao controlador ou de encaminhamento para outra RSU
                if(!mappedServiceMsg.get("rsuServiceRunner").contains("rsu")){
                    //Não possui RSU, enviar para o controlador.
                    this.sendRsuRunnerRequest(serviceMsg);
                    getLog().infoSimTime(this, "Request RsuServiceRunner to Controller for message: {}", serviceMsg.toString());
                }else{
                    //Se possuir um RSU runner, enviar para o RSURunner.
                    this.sendServiceMsgToOtherRsu(mappedServiceMsg);
                    getLog().infoSimTime(this, "Forward Service Msg to Runner Rsu: {}", mappedServiceMsg.get("rsuServiceRunner"));

                }

            }else
            if(resource instanceof CarInfoToVfnMsg) {

                CarInfoToVfnMsg message = (CarInfoToVfnMsg) resource;
                // message was passed from another app in the same vehicle
                getLog().infoSimTime(this, "Received message from other application: {}", message.toString());
                sendVehicleInfoToVfnServer(message);
            }else
            if(resource instanceof VfnServiceResultMsg){
                getLog().infoSimTime(this,"Returning process result to RSU: {}", resource.toString());
                getOs().getCellModule().sendV2xMessage((VfnServiceResultMsg)resource);
            }



        }


    }

    @Override
    public void onShutdown()
    {
        getLog().infoSimTime(this, "Tear down");
    }

    @Override
    public void onMessageReceived(ReceivedV2xMessage receivedV2xMessage) {
        if(receivedV2xMessage.getMessage().getRouting().getSource().getSourceName().contains("veh_")) return; //Não analisa mensagens da rede Adhoc, apenas da VFN.
        V2xMessage fullMsg = receivedV2xMessage.getMessage();
        if (fullMsg instanceof ControllerServerMsg) {
            getLog().infoSimTime(this,"ServiceRunnerMsg arrived via CellNetwork:{}",fullMsg.toString());
            //RSU recebe uma mensagem que informa qual o executor de um serviço para um determinado veículo
            //Repassar para a aplicação que opera na base de dados
            final Event event = new Event(
                    getOs().getSimulationTime(),
                    this.appMap.get(RsuKnowledgeBaseApp.class.getName()), //Define qual aplicação processará o evento
                    receivedV2xMessage
            );
            this.getOs().getEventManager().addEvent(event);
        }else
        if(fullMsg instanceof VfnServiceMsg){
            //mensagem pode ser vinda de um veículo ou vinda de outra RSU
            if(fullMsg.getRouting().getSource().getSourceName().contains("rsu")){
                //mensagem encaminhada de outra RSU
                getLog().infoSimTime(this,"ForwardedServiceMsg arrived via CellNetwork: {}", fullMsg.toString());
                //enviar a mensagem para a aplicação controladora do RSU
                final Event event = new Event(
                        getOs().getSimulationTime(),
                        this.appMap.get(RsuKnowledgeBaseApp.class.getName()), //Define qual aplicação processará o evento
                        receivedV2xMessage
                );
                this.getOs().getEventManager().addEvent(event);
            }
        }else
            if(fullMsg instanceof VfnServiceResultMsg){
                //mensagem de resposta chegou provinda de uma RSU.
                //retirar da lista de encaminhamentos
                //encaminhar para o vecículo solicitante
                String coreMsg = MsgUtils.extractCoreMsg(fullMsg);
                getLog().infoSimTime(this,"Received VfnServiceResultMsg process result from {}: {}", fullMsg.getRouting().getSource().getSourceName(), coreMsg);
                HashMap<String,String> mappedResultServiceMsg = MsgUtils.extractMsgToHash(fullMsg.toString());
                MessageRouting  msgRoute = getOs().getAdHocModule().createMessageRouting().viaChannel(AdHocChannel.CCH).topoCast(mappedResultServiceMsg.get("vhId"),5);
                final VfnServiceResultMsg serviceResultV2xMsg;
                serviceResultV2xMsg = new VfnServiceResultMsg(msgRoute,coreMsg);
                final Event event = new Event(getOs().getSimulationTime(),this.appMap.get(RsuAdHocMsgExchangeApp.class.getName()), serviceResultV2xMsg);
                this.getOs().getEventManager().addEvent(event);

            }

    }

    @Override
    public void onAcknowledgementReceived(ReceivedAcknowledgement receivedAcknowledgement) {
        //getLog().info("Acknowlegment message received");
    }

    @Override
    public void onCamBuilding(CamBuilder camBuilder) {

    }

    @Override
    public void onMessageTransmitted(V2xMessageTransmission v2xMessageTransmission) {
       //getLog().infoSimTime(this, "Mensagem Transmitida com sucesso via CellNetwork: {}", v2xMessageTransmission.getMessage());

    }

    public void sendVehicleInfoToVfnServer (V2xMessage v2xPeriodicVehicleMsg){
        String coreMsg = extractCoreMsg(v2xPeriodicVehicleMsg);
        final MessageRouting routing = getOs().getCellModule().createMessageRouting()
                .protocol(ProtocolType.TCP)
                .topoCast(this.serverName);
        V2xMessage fullMsg = v2xPeriodicVehicleMsg;
        if(fullMsg instanceof CarInfoToVfnMsg) {
            getOs().getCellModule().sendV2xMessage(new CarInfoToVfnMsg(routing, coreMsg));
        }
        if(fullMsg instanceof VfnServiceMsg) {
            getOs().getCellModule().sendV2xMessage(new VfnServiceMsg(routing, coreMsg));
        }

    }

    public void sendRsuRunnerRequest (VfnServiceMsg v2xVfnServiceMsg){
        final MessageRouting routing = getOs().getCellModule().createMessageRouting()
                .protocol(ProtocolType.TCP)
                .topoCast(this.serverName);
        getOs().getCellModule().sendV2xMessage(new VfnServiceMsg(routing, v2xVfnServiceMsg.getCoreMsg()));

    }

    public void sendServiceMsgToOtherRsu (HashMap<String, String> mappedServiceMsg){
        /*
        String coreMsg = "#"+
                "vhId="+mappedServiceMsg.get("vhId")+";"+
                "rsuId="+mappedServiceMsg.get("rsuId")+";"+
                "msgId="+mappedServiceMsg.get("msgId")+";"+
                "latitude="+mappedServiceMsg.get("latitude")+";"+
                "longitude="+mappedServiceMsg.get("longitude")+";"+
                "speed="+mappedServiceMsg.get("speed")+";"+
                "serviceId="+mappedServiceMsg.get("serviceId")+";"+
                "rsuServiceRunner="+mappedServiceMsg.get("rsuServiceRunner")+";"+
                "serviceMsg="+mappedServiceMsg.get("serviceMsg")+";"+
                "#";

         */
        final MessageRouting routing = getOs().getCellModule().createMessageRouting()
                .protocol(ProtocolType.TCP)
                .topoCast(mappedServiceMsg.get("rsuServiceRunner"));
        VfnServiceMsg vfnServiceMsg =  new VfnServiceMsg(routing,mappedServiceMsg);
        getOs().getCellModule().sendV2xMessage(vfnServiceMsg);

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
