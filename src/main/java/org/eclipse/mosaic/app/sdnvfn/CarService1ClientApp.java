package org.eclipse.mosaic.app.sdnvfn;

import org.eclipse.mosaic.app.sdnvfn.config.VehicleConfig;
import org.eclipse.mosaic.app.sdnvfn.information.RsuAnnouncedInfo;
import org.eclipse.mosaic.app.sdnvfn.message.VfnServiceMsg;
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
import org.eclipse.mosaic.lib.objects.vehicle.VehicleData;
import org.eclipse.mosaic.lib.util.scheduling.Event;
import org.eclipse.mosaic.lib.enums.AdHocChannel;
import org.eclipse.mosaic.lib.objects.v2x.MessageRouting;

import org.eclipse.mosaic.rti.TIME;

import edu.umd.cs.findbugs.annotations.Nullable;

import java.awt.Color;
import java.lang.String;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

public class CarService1ClientApp extends ConfigurableApplication<VehicleConfig, VehicleOperatingSystem> implements VehicleApplication, CommunicationApplication {

    private final static long MESSAGE_TIME_OUT = 10*TIME.SECOND;
    private final static long MAX_TIME_WITHOUT_RSU_BEACON = 3*TIME.SECOND;
    private final static int MAX_RETRY_MESSAGES = 3;

    private String serviceId;
    private String serviceDataToProcess;
    private Integer resultOfProcess;
    private float msgSentTime;
    private Integer term1;
    private Integer term2;

    private VehicleConfig vhConfig;
    private Integer msgSequenceCounter;
    private int resultMsgSequenceCounter;
    private int retryMsgCounter;

    private String sentMsgKey;
    private boolean retrySendMsg;
    private String sentCoreMsg;
    private long lastResultMsgTime;
    private String lastRsuAccessPointId;
    private String serviceStrData;
    private String lastMsgId;
    private HashMap<String,String> mappedVfnServiceResultMsg;
    private HashMap<String, String> mappedVfnServiceMsg;

    private List<? extends Application> applicationList;
    private HashMap<String,Application> appMap;




    public CarService1ClientApp() {
        super(VehicleConfig.class, "VehicleConfiguration");
    }

    @Override
    public void onStartup() {
        this.vhConfig = this.getConfiguration();  //load ConfigFile to config object
        this.applicationList = getOs().getApplications(); //gera a lista de aplicações da Unidade (veículo)
        this.appMap = new HashMap<>();
        this.convertAppListToMap(); //Armazena a lista de Aplicações da Unidade em Uma Lista que pode ser consultada.
        this.retrySendMsg = false;
        this.lastResultMsgTime = 0L;

        if(!this.vhConfig.vehicleServiceList.contains("service_01")){
            getLog().infoSimTime(this, "Não encontrei o serviço");
            return;
        }
        this.msgSentTime = getOs().getSimulationTime();
        this.msgSequenceCounter = 0;
        this.resultMsgSequenceCounter = 0;
        this.resultOfProcess= 100;
        this.serviceId = "service_01";
        this.serviceStrData = "";
        this.lastMsgId = "";
        this.mappedVfnServiceResultMsg = new HashMap<>(); //Instancia o MAP que conterá a mensagem de resultado que for recebida
        this.mappedVfnServiceMsg = new HashMap<>(); //Instancia o MAP que conterá a mensagem de resultado que enviada
        this.retryMsgCounter = 0;

        //getLog().infoSimTime(this, "Activating Adhoc Communication");
        //Ativação do módulo de comunicação Adhoc

        /*getOs().getAdHocModule().enable(new AdHocModuleConfiguration()
                .addRadio()
                .channel(AdHocChannel.CCH)
                .distance(this.vhConfig.radioRange)
                .create());

        //scheduleServiceMsgSending();

         */
        NetUtils.createAdHocInterface(this,this.vhConfig.radioRange,AdHocChannel.CCH);
    }



    public void sendCarToServiceMsg(String serviceStrData) {
        final MessageRouting msgRoute =
                getOs().getAdHocModule().createMessageRouting().viaChannel(AdHocChannel.CCH).topoCast(this.vhConfig.rsuAccessPointId,this.vhConfig.defautMsgHops);
        final VfnServiceMsg vfnServiceMsg = new VfnServiceMsg(msgRoute,serviceStrData); //VfnServiceMsg extends V2xMessage
        getOs().getAdHocModule().sendV2xMessage(vfnServiceMsg); //envio de mensagem
        getLog().infoSimTime(this, "\n---------------\nSend Service message to VFN via {}", this.vhConfig.rsuAccessPointId);
        getLog().info( "Message Content: {}\n ------------------------------------------------------",vfnServiceMsg.getCoreMsg());
        this.sentCoreMsg = vfnServiceMsg.getCoreMsg();
        this.msgSentTime = getOs().getSimulationTime();

    }
    public void resetRetryMsgCounter(){
        this.retryMsgCounter = 0;
    }
    public void incrementRetryMsgCounter(){
        if(this.retryMsgCounter<=MAX_RETRY_MESSAGES){
            this.retryMsgCounter++;
        }
    }

    public void retrySendVfnMsg(String serviceStrData){
        this.incrementRetryMsgCounter();
        this.sendCarToServiceMsg(serviceStrData);
    }

    public String generateServiceStrData(){
        String serviceStrData;
        this.term1 = 5;
        this.term2 = this.resultOfProcess;
        this.serviceDataToProcess = "term1:"+this.term1.toString()+"&term2:"+this.term2.toString();
        serviceStrData =
                "#vhId="+ Objects.requireNonNull(getOs().getVehicleData()).getName() +
                ";latitude="+ Objects.requireNonNull(getOs().getVehicleData()).getPosition().getLatitude() +
                ";longitude="+ Objects.requireNonNull(getOs().getVehicleData()).getPosition().getLongitude() +
                ";speed="+ Objects.requireNonNull(getOs().getVehicleData()).getSpeed() +
                ";serviceId="+ this.serviceId +
                ";rsuServiceRunner=-1" +
                ";source=" + getOs().getId() +
                ";msgId="+ generateMsgId()+
                ";serviceMsg="+this.serviceDataToProcess+
                "#";
        return serviceStrData;
    }


    /*
    This menthod generate the message ID of a message to be sent
     */
    public String generateMsgId(){
        String[] splitedVhId = getOs().getId().split("_");
        String[] splitedRsuId = this.vhConfig.rsuAccessPointId.split("_");
        String[] splitedServiceId = this.serviceId.split("_");
        this.sentMsgKey = splitedVhId[1]+"_"+splitedServiceId[1]+"_"+splitedRsuId[1]+"_"+ this.msgSequenceCounter.toString();
        return this.sentMsgKey;
    }

    public void scheduleServiceMsgSending() {

        Event event = new Event(getOs().getSimulationTime()+TIME.SECOND, this,"service01MsgSend"); //cria evento imediato para envio de mensagem
        getOs().getEventManager().addEvent(event);
    }

    public void scheduleServiceMsgTimeOutCheck(){
        String eventMsgCheck = "service01MsgTimeOut:"+this.msgSequenceCounter;
        Event event = new Event((getOs().getSimulationTime()+MESSAGE_TIME_OUT), this,eventMsgCheck); //cria evento para checar o recebimento de resposta a uma mensagem enviada
        getOs().getEventManager().addEvent(event);
    }





    // método a ser invocado no momento do evento criado no método on startup.
    // É necessário criar novo evento sempre após a execução deste.
    @Override
    public void processEvent(Event event){
        Object resource = event.getResource();

        if(resource instanceof RsuAnnouncedInfo){
            //Cada vez que o RSU access point for alterado ou estiver a mais de 3 segundos sem conexão, as aplicações serão avisadas com os dados do RSU access point.
            RsuAnnouncedInfo electedRsu = (RsuAnnouncedInfo) resource;
            //electedRsu.setBeaconArrivedTime(getOs().getSimulationTime());
            getLog().infoSimTime(this,"Arrived Elected RSU info: {}", electedRsu.getRsuId());
            //this.lastRsuBeaconTime = electedRsu.getBeaconArrivedTime(); //Last RSU update
            getLog().infoSimTime(this,"New RSU accessPoint for VFN Communications: {}",electedRsu.getRsuId());
            if(Objects.equals(this.vhConfig.rsuAccessPointId, "null")) {
                //Se for primeira conexão ou reconexão, significa que o cliente não estava transmitindo. Portanto reiniciar transmissão
                getLog().infoSimTime(this, "INICIO DE CONSUMO DE SERVIÇO");
                this.vhConfig.rsuAccessPointId = electedRsu.getRsuId(); //Atualiza quem é o RSU access point do Veículo.
                scheduleServiceMsgSending(); //cria evento de envio de mensagem do cliente para o RSU para consumir serviço.

            }else{
                //Caso o RSU já esteja em conexão, apenas atualizar
                this.vhConfig.rsuAccessPointId = electedRsu.getRsuId(); //Atualiza quem é o RSU access point do Veículo.
            }

        }else
        if(resource instanceof String){
            String msg = (String) resource;
            if(msg.equals("service01MsgSend")){
                //this.mappedServiceResultMsg.clear();
                //envia mensagem se houver conexão um RSU
                if(Objects.equals(this.vhConfig.rsuAccessPointId, "null"))return; //cancela o envio no caso de não haver RSU AccessPoint
                if(!this.retrySendMsg)this.msgSequenceCounter++; //se não houver pedido de renvio de mensagem, incrementa o contador de sequencia de mensagem
                this.serviceStrData = this.generateServiceStrData();
                this.mappedVfnServiceMsg.clear();
                this.mappedVfnServiceMsg = MsgUtils.extractMsgToHash(serviceStrData);
                this.sendCarToServiceMsg(this.serviceStrData);
                this.scheduleServiceMsgTimeOutCheck();

            }else if(msg.contains("service01MsgTimeOut")){
                //se não recebeu nenhum resultado após o timeout, reenvia a mensagem
                String[] msgArray = msg.split(":");
                int msgSequence = Integer.parseInt(msgArray[1]);
                //Se já recebeu resposta da mensagem, encerra a checagem de TimeOut
                if(this.resultMsgSequenceCounter>=msgSequence) return;

                //Se não recebeu resposta da mensagem, solicita o reenvio da mensagem
                this.retrySendMsg = true;
                getLog().infoSimTime(this,"Tempo de resposta excedido");
                getLog().info("------------------------------");
                if(this.retryMsgCounter<=MAX_RETRY_MESSAGES){
                    this.retryMsgCounter++;
                    scheduleServiceMsgSending(); //cria evento de envio de mensagem do cliente para o RSU para consumir serviço.
                }else{
                    getLog().infoSimTime(this,"Excedida a quantidade de tentativas: A reiniciar a conexão com a VFN...");
                    this.retryMsgCounter = 0;
                    this.vhConfig.rsuAccessPointId = "null";
                    getOs().requestVehicleParametersUpdate()
                            .changeColor(Color.RED)
                            .apply();
                    this.dispachStrToApp("resetRsu", CarV2xReceiverApp.class); //solicita o reset de RSU para a aplicação de recebimento de mensagens
                }
            }

        }else if(resource instanceof VfnServiceResultMsg){

            VfnServiceResultMsg vfnServiceResultMsg = (VfnServiceResultMsg) resource;
            this.mappedVfnServiceResultMsg.clear(); //limpa a ultima resposta mapeada
            this.mappedVfnServiceResultMsg = MsgUtils.extractMsgToHash(vfnServiceResultMsg.toString()); //mapea a mensagem recebida
            getLog().infoSimTime(this,"Resultado recebido: ");
            getLog().info("Msg Id: {}",this.mappedVfnServiceResultMsg.get("msgId"));
            getLog().info("Result: {}",this.mappedVfnServiceResultMsg.get("serviceProcessResult"));
            //checar se a mensagem recebida refere-se à mensagem enviada
            if(!Objects.equals(mappedVfnServiceResultMsg.get("msgId"), mappedVfnServiceMsg.get("msgId"))) return; //não processa resposta que não corresponda à última mensagem enviada

            this.resetRetryMsgCounter();//ao receber um resposta, reseta o contador de tentativas de envio
            this.retrySendMsg = false; //indica que a proxima mensagem não será de retry.

            this.lastResultMsgTime = getOs().getSimulationTime(); //registra o momento do recebimento de dados da VFN.
            getLog().infoSimTime(this,"Processando o Resultado Recebido: msgId={}", mappedVfnServiceResultMsg.get("msgId"));
            this.resultOfProcess = Integer.parseInt(mappedVfnServiceResultMsg.get("serviceProcessResult"));
            this.resultMsgSequenceCounter++;

            scheduleServiceMsgSending(); //dispara o envio da próxima mensagem


        }
    }

    @Nullable
    @Override
    public void onVehicleUpdated(VehicleData previousVehicleData, VehicleData updatedVehicleData) {

    }

    @Override
    public void onShutdown() {
        //getLog().info("Ending Geolocation sendingApp  via TopoCast");
    }

    @Override
    public void onMessageReceived(ReceivedV2xMessage receivedV2xMessage) {

    }

    public void dispachStrToApp(String strMsg, Class appClass){
        //Comunicando com outra aplicação via EventProcess
        final Event sendIntraUnitMsg = new Event(getOs().getSimulationTime(),this.appMap.get(appClass.getName()),strMsg);
        this.getOs().getEventManager().addEvent(sendIntraUnitMsg);
    }
    private void convertAppListToMap(){
        for (Application application : this.applicationList) {
            //Criar uma hash com as aplicações da Unidade
            //Inserir cada aplicação na Hash
            String[] strSplit = application.toString().split("@");
            this.appMap.put(strSplit[0],application);
        }
    }

    @Override
    public void onAcknowledgementReceived(ReceivedAcknowledgement receivedAcknowledgement) {
        getLog().info(" acknowlegment Message receiced");

    }

    @Override
    public void onCamBuilding(CamBuilder camBuilder) {

    }

    @Override
    public void onMessageTransmitted(V2xMessageTransmission v2xMessageTransmission) {

    }
}
