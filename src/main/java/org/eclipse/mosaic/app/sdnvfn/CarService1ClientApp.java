package org.eclipse.mosaic.app.sdnvfn;

import org.eclipse.mosaic.app.sdnvfn.config.VehicleConfig;
import org.eclipse.mosaic.app.sdnvfn.information.RsuAnnouncedInfo;
import org.eclipse.mosaic.app.sdnvfn.message.GenericV2xMessage;
import org.eclipse.mosaic.app.sdnvfn.network.CommunicationInterface;
import org.eclipse.mosaic.app.sdnvfn.utils.IntraUnitAppInteractor;
import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.CamBuilder;
import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.ReceivedAcknowledgement;
import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.ReceivedV2xMessage;
import org.eclipse.mosaic.fed.application.app.ConfigurableApplication;
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
import java.util.Objects;

public class CarService1ClientApp extends ConfigurableApplication<VehicleConfig, VehicleOperatingSystem> implements VehicleApplication, CommunicationApplication {

    public CarService1ClientApp() {
        super(VehicleConfig.class, "VehicleConfiguration");
    }
    private final static long MESSAGE_TIME_OUT = 2*TIME.SECOND;
    private final static int MAX_RETRY_MESSAGES = 1;

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
    private boolean retryMsg;
    private String sentCoreMsg;
    private long lastResultMsgTime;
    private String serviceStrData;
    private CommunicationInterface communicationInterface;
    private IntraUnitAppInteractor intraUnitAppInteractor;




    @Override

    public void onStartup() {
        this.vhConfig = this.getConfiguration();  //load ConfigFile to config object
        communicationInterface = new CommunicationInterface(this);
        intraUnitAppInteractor = new IntraUnitAppInteractor(this);
        this.retryMsg = false;
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
        this.retryMsgCounter = 0;

        communicationInterface.createAdHocInterface(this.vhConfig.radioRange,AdHocChannel.CCH);
    }



    public void sendCarToServiceMsg(String serviceStrData) {
        final MessageRouting msgRoute =
                getOs().getAdHocModule().createMessageRouting().viaChannel(AdHocChannel.CCH).topoCast(this.vhConfig.rsuAccessPointId,this.vhConfig.defautMsgHops);
        final GenericV2xMessage vfnServiceMsg = new GenericV2xMessage(msgRoute,serviceStrData); //VfnServiceMsg extends V2xMessage
        //vfnServiceMsg.setSequenceNumber(this.msgSequenceCounter);
        vfnServiceMsg.mappedV2xMsg.put("sequenceCounter",this.msgSequenceCounter.toString());
        getOs().getAdHocModule().sendV2xMessage(vfnServiceMsg); //envio de mensagem
        //getLog().infoSimTime(this, "RSU-AP= {}, ", this.vhConfig.rsuAccessPointId);
        getLog().infoSimTime(this ,"sent_vfnServiceMsg: {}",vfnServiceMsg.getCoreMsg());
        this.sentCoreMsg = serviceStrData;
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
                "#vhId="+ Objects.requireNonNull(getOs().getVehicleData()).getName()+
                ";msgType="+this.vhConfig.vfnServiceMsgType+
                ";sendTime="+getOs().getSimulationTime()+
                ";netDestAddress="+this.vhConfig.rsuNet+
                ";rsuId="+this.vhConfig.rsuAccessPointId+
                ";unitDestId="+this.vhConfig.rsuAccessPointId+
                ";latitude="+ Objects.requireNonNull(getOs().getVehicleData()).getPosition().getLatitude()+
                ";longitude="+ Objects.requireNonNull(getOs().getVehicleData()).getPosition().getLongitude()+
                ";heading="+ Objects.requireNonNull(getOs().getVehicleData()).getHeading().toString()+
                ";speed="+ Objects.requireNonNull(getOs().getVehicleData()).getSpeed()+
                ";aceleration="+ Objects.requireNonNull(getOs().getVehicleData()).getLongitudinalAcceleration().toString()+
                ";serviceId="+ this.serviceId+
                ";rsuServiceRunner=-1"+
                ";source=" + getOs().getId()+
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

        Event event = new Event(getOs().getSimulationTime()+TIME.SECOND/3, this,"service01MsgSend"); //cria evento imediato para envio de mensagem
        getOs().getEventManager().addEvent(event);
    }

    public void scheduleServiceMsgTimeOutCheck(){
        String eventMsgCheck = "service01MsgTimeOut:"+this.msgSequenceCounter;
        Event event = new Event((getOs().getSimulationTime()+MESSAGE_TIME_OUT), this,eventMsgCheck); //cria evento para checar o recebimento de resposta a uma mensagem enviada
        getOs().getEventManager().addEvent(event);
    }


    /**
     * Método é disparado pela aplicação de recebimento de mensagens.
     * @param event
     */
    @Override
    public void processEvent(Event event){
        Object resource = event.getResource();

        if(resource instanceof String){
            String msg = (String) resource;
            if(msg.equals("service01MsgSend")){
                //this.mappedServiceResultMsg.clear();
                //envia mensagem se houver conexão com um RSU
                if(Objects.equals(this.vhConfig.rsuAccessPointId, "null"))return; //cancela o envio no caso de não haver RSU AccessPoint
                if(!this.retryMsg)this.msgSequenceCounter++; //se não houver pedido de renvio de mensagem, incrementa o contador de sequencia de mensagem
                this.serviceStrData = this.generateServiceStrData();
                this.sendCarToServiceMsg(this.serviceStrData);
                this.scheduleServiceMsgTimeOutCheck();

            }else if(msg.contains("service01MsgTimeOut")){
                //Verificação se houve time-out
                String[] msgArray = msg.split(":");
                int msgSequence = Integer.parseInt(msgArray[1]);

                if(this.resultMsgSequenceCounter>=msgSequence) return;//Se já recebeu resposta da mensagem, encerra a checagem de TimeOut

                //Se não recebeu resposta da mensagem, solicita o reenvio da mensagem
                this.retryMsg = true;
                getLog().infoSimTime(this,"re-send");
                //getLog().info("------------------------------");
                if(this.retryMsgCounter<=MAX_RETRY_MESSAGES){
                    this.retryMsgCounter++;
                    scheduleServiceMsgSending(); //cria evento de envio de mensagem do cliente para o RSU para consumir serviço.
                }else{
                    getLog().infoSimTime(this,"stop-transmit...");
                    this.retryMsgCounter = 0;
                    this.vhConfig.rsuAccessPointId = "null";
                    getOs().requestVehicleParametersUpdate().changeColor(Color.YELLOW).apply();
                    intraUnitAppInteractor.sendStrToApp("resetRsu", CarV2xInterfaceApp.class);  //solicita o reset de RSU para a aplicação de recebimento de mensagens
                }
            }

        }else
        if(resource instanceof RsuAnnouncedInfo){
            //Cada vez que o RSU access point for alterado ou estiver a mais de 3 segundos sem conexão, as aplicações serão avisadas com os dados do RSU access point.
            RsuAnnouncedInfo electedRsu = (RsuAnnouncedInfo) resource;
            //getLog().infoSimTime(this,"\n\nArrived Elected RSU info: {}", electedRsu.getRsuId());
            getLog().infoSimTime(this,"new_RSU_AP: {}",electedRsu.getRsuId());
            if(Objects.equals(this.vhConfig.rsuAccessPointId, "null")) {
                //Se for primeira conexão ou reconexão, significa que o cliente não estava transmitindo. Portanto reiniciar transmissão
                //getLog().infoSimTime(this, "INICIO DE CONSUMO DE SERVIÇO");
                getOs().requestVehicleParametersUpdate().changeColor(Color.YELLOW).apply();
                this.vhConfig.rsuAccessPointId = electedRsu.getRsuId(); //Atualiza quem é o RSU access point do Veículo.
                scheduleServiceMsgSending(); //cria evento de envio de mensagem do cliente para o RSU para consumir serviço.

            }else{
                //Caso o RSU já esteja em conexão, apenas atualizar
                this.vhConfig.rsuAccessPointId = electedRsu.getRsuId(); //Atualiza quem é o RSU access point do Veículo.
                //scheduleServiceMsgSending(); //cria evento de envio de mensagem do cliente para o RSU para consumir serviço.
            }

        }else
            if(resource instanceof GenericV2xMessage){
                GenericV2xMessage vfnServiceResultMsg = (GenericV2xMessage) resource;
                if(Objects.equals(vfnServiceResultMsg.getMsgType(), this.vhConfig.serviceResultMsgType)){
                    //this.mappedVfnServiceResultMsg.clear(); //limpa a ultima resposta mapeada
                    //this.mappedVfnServiceResultMsg = MsgUtils.extractMsgToHash(vfnServiceResultMsg.toString()); //mapea a mensagem recebida
                    //getLog().infoSimTime(this,"Resultado recebido de {}: ",vfnServiceResultMsg.getRouting().getSource().getSourceName());
                    getLog().infoSimTime(this,"vfnServiceResultMsg: {} ",vfnServiceResultMsg.getCoreMsg());
                    //getLog().info("Msg Id: {}",vfnServiceResultMsg.mappedV2xMsg.get("msgId"));
                    //getLog().info("Result: {}",vfnServiceResultMsg.mappedV2xMsg.get("serviceProcessResult"));

                    //checar se a mensagem recebida refere-se à mensagem enviada
                    if(!Objects.equals(this.sentMsgKey, vfnServiceResultMsg.mappedV2xMsg.get("msgId"))) return; //não processa resposta que não corresponda à última mensagem enviada

                    this.resetRetryMsgCounter();//ao receber um resposta, reseta o contador de tentativas de envio
                    this.retryMsg = false; //indica que a proxima mensagem não será de retry.

                    this.lastResultMsgTime = getOs().getSimulationTime(); //registra o momento do recebimento de dados da VFN.
                    //getLog().infoSimTime(this,"Processando o Resultado Recebido: msgId={}", vfnServiceResultMsg.mappedV2xMsg.get("msgId"));
                    this.resultOfProcess = Integer.valueOf(vfnServiceResultMsg.mappedV2xMsg.get("serviceProcessResult"));
                    this.resultMsgSequenceCounter++;

                    scheduleServiceMsgSending(); //dispara o envio da próxima mensagem


                }
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

    @Override
    public void onAcknowledgementReceived(ReceivedAcknowledgement receivedAcknowledgement) {
        getLog().info(" acknowlegment Message receiced");

    }

    @Override
    public void onCamBuilding(CamBuilder camBuilder) {

    }

    @Override
    public void onMessageTransmitted(V2xMessageTransmission v2xMessageTransmission) {
        getOs().requestVehicleParametersUpdate().changeColor(Color.BLUE).apply();
    }
}
