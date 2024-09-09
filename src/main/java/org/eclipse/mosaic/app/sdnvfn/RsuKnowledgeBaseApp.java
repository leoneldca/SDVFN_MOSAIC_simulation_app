
package org.eclipse.mosaic.app.sdnvfn;

import org.eclipse.mosaic.app.sdnvfn.config.RsuConfig;
import org.eclipse.mosaic.app.sdnvfn.information.DynamicVehicleMap;
import org.eclipse.mosaic.app.sdnvfn.information.RsuConnectedVehicle;
import org.eclipse.mosaic.app.sdnvfn.message.*;
import org.eclipse.mosaic.app.sdnvfn.utils.IntraUnitAppInteractor;
import org.eclipse.mosaic.app.sdnvfn.utils.LogUtils;
import org.eclipse.mosaic.app.sdnvfn.utils.MsgUtils;
import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.ReceivedV2xMessage;
import org.eclipse.mosaic.fed.application.app.ConfigurableApplication;
import org.eclipse.mosaic.fed.application.app.api.Application;
import org.eclipse.mosaic.fed.application.app.api.os.RoadSideUnitOperatingSystem;
import org.eclipse.mosaic.lib.enums.AdHocChannel;
import org.eclipse.mosaic.lib.enums.ProtocolType;
import org.eclipse.mosaic.lib.objects.v2x.MessageRouting;
import org.eclipse.mosaic.lib.objects.v2x.etsi.Cam;
import org.eclipse.mosaic.lib.objects.v2x.etsi.cam.VehicleAwarenessData;
import org.eclipse.mosaic.lib.util.scheduling.Event;
import org.eclipse.mosaic.rti.TIME;

import java.util.*;

/**
 * Class used as a Knowledge Base to the RSU.
 */
public  class RsuKnowledgeBaseApp extends ConfigurableApplication<RsuConfig,RoadSideUnitOperatingSystem> {

    private DynamicVehicleMap localDynamicVehicleMap;
    private IntraUnitAppInteractor applicationsInteractor;
    private HashMap<String, RsuConnectedVehicle> connectedVehiclesMap;
    private List<? extends Application> applicationList;
    private HashMap<String,Application> appMap;

    private HashMap<String,String> waitingServiceMsgMap;
    private HashMap<String,String> forwardedMsgMap;
    //private HashMap<String,String> mappedServiceMsg;
    private String processResultStr;
    public int messageCounter;
    private RsuConfig rsuConfig;

    public RsuKnowledgeBaseApp() {
        super(RsuConfig.class, "RsuConfiguration");
    }


    @Override
    public void onStartup() {
        ///Carrega configurações de RSU do arquivo RsuConfiguration.json
        this.rsuConfig = this.getConfiguration();  //load ConfigFile into config object
        ///Instanciação de Interador de Mensagens IntraUnit
        applicationsInteractor = new IntraUnitAppInteractor(this);

        getLog().infoSimTime(this, "Enabling RsuKnowledgeBase in {} ",getOs().getId());
        this.localDynamicVehicleMap = new DynamicVehicleMap();

        this.waitingServiceMsgMap = new HashMap<>();  //inicia a lista de mensagens em espera
        this.forwardedMsgMap = new HashMap<>(); //inicia a lista de mensagens encaminhadas
        this.connectedVehiclesMap = localDynamicVehicleMap.getVehiclesMap(); //Mapa de veículos conectados
        this.messageCounter = 0;
    }

    /**
     * Método encaminha mensagem de resposta de serviço da RSU para o veículo solicitante que esteja conectado na RSU atual
     */
    private void sendResultMsgToVehicle(MessageRouting msgRoute, HashMap<String,String> mappedServiceMsg){
        HashMap<String,String> mappedResultMsg = new HashMap<>();
        mappedResultMsg.put("msgType",this.rsuConfig.serviceResultMsgType);
        mappedResultMsg.put("unitDestId",mappedServiceMsg.get("vhId"));
        mappedResultMsg.put("netDestAddress",this.rsuConfig.vehicleNet);
        mappedResultMsg.put("serviceProcessResult",this.processResultStr);
        mappedResultMsg.put("vhId",mappedServiceMsg.get("vhId"));
        mappedResultMsg.put("msgId",mappedServiceMsg.get("msgId"));
        mappedResultMsg.put("rsuId",getOs().getId());

        final VfnServiceResultMsg serviceResultV2xMsg;
        serviceResultV2xMsg = new VfnServiceResultMsg(msgRoute, MsgUtils.getMsgFromStringMap(mappedResultMsg));
        applicationsInteractor.sendV2xMsgToApp(serviceResultV2xMsg,RsuOFSwitchApp.class);

    }
    public void checkVehicleDisconnection(RsuConnectedVehicle connectedVehicle){
        final Event checkDisconnection = new Event(getOs().getSimulationTime()+3*TIME.SECOND,this,connectedVehicle);
        getOs().getEventManager().addEvent(checkDisconnection);
    }

    private void carInfoToVfnMsgHandler(GenericV2xMessage carBeaconMsg){
        //Ações a serem realizadas ao receber uma mensagem de beacom de veículos
        //getLog().infoSimTime(this,"carBeaconMsg_from: {}",carBeaconMsg.mappedV2xMsg.get("vhId"));
        //getLog().infoSimTime(this,"carBeaconMsg:{}",carBeaconMsg.getCoreMsg());

        //carBeaconMsg.mappedV2xMsg.put("rsuId",getOs().getId()); //adicionando o rsuId do RSU de acesso ao mapeamento
        //LogUtils.mappedMsgLog(this,this.getLog(),carBeaconMsg.mappedV2xMsg,"---------------------------MappedMsg-CarInfoToVfnMsg---------------------");
        this.localDynamicVehicleMap.addVehicleInfo(carBeaconMsg.mappedV2xMsg);
        this.checkVehicleDisconnection(this.localDynamicVehicleMap.getVehiclesMap().get(carBeaconMsg.mappedV2xMsg.get("vhId")));


    }
    private void rsuServiceMsgHandler(GenericV2xMessage vfnServiceMsg){
        //Mensagem vinda de RSU por encaminhamento
        //RSU de processamento consta na mensagem
        localDynamicVehicleMap.addVehicleInfo(vfnServiceMsg.getMappedMsg()); //atualiza os dados do veículo na LDM
        //this.checkVehicleDisconnection(this.localDynamicVehicleMap.getVehiclesMap().get(vfnServiceMsg.mappedV2xMsg.get("vhId")));
        //neste momento os dados do veículo são atualizados na RSU que recebe a mensagem de processamento.
        this.processResultStr =  runService(vfnServiceMsg.getMappedMsg());
        getLog().infoSimTime(this,"Send result to RSU witch the vehicle {} is connected: {}",vfnServiceMsg.mappedV2xMsg.get("vhId"), vfnServiceMsg.mappedV2xMsg.get("rsuId"));
        //Envio de resultado para a RSU de Origem, onde o veículo está conectado
        final MessageRouting msgRoute = getOs().getCellModule().createMessageRouting().protocol(ProtocolType.TCP).topoCast(vfnServiceMsg.getRsuAPId());
        HashMap<String,String> mappedResultMsg = new HashMap<>();
        mappedResultMsg.put("msgType",this.rsuConfig.serviceResultMsgType);
        mappedResultMsg.put("unitDestId","rsu_"+ extractRsuApIdFromMsgId(vfnServiceMsg.mappedV2xMsg.get("msgId"))); //no ID da mensagens pode-se saber a qual RSU o veículo está conectado
        mappedResultMsg.put("netDestAddress",this.rsuConfig.rsuNet);
        mappedResultMsg.put("serviceProcessResult",this.processResultStr);
        mappedResultMsg.put("vhId",vfnServiceMsg.mappedV2xMsg.get("vhId"));
        mappedResultMsg.put("msgId",vfnServiceMsg.mappedV2xMsg.get("msgId"));
        mappedResultMsg.put("rsuId",getOs().getId()); //verificar se realmente é necessário ou deve-se manter o original do rsuAp

        final GenericV2xMessage serviceResultV2xMsg;
        serviceResultV2xMsg = new GenericV2xMessage(msgRoute, mappedResultMsg);
        applicationsInteractor.sendV2xMsgToApp(serviceResultV2xMsg,RsuOFSwitchApp.class);
    }

    public String extractRsuApIdFromMsgId(String msgId){
        String[] fields = msgId.split("_");
        return fields[2];

    }

    /**
     * Este método se destina a tratar mensagens de serviço vindas de veículos situados na rede Ad-hoc
     *
     * @param v2xMessage
     */
    private void vehicleServiceMsgHandler(GenericV2xMessage v2xMessage){
        RsuConnectedVehicle vhInfo;
        String registeredRsuServiceRunner = "";
        //Nova mensagem vinda de veículos
        //v2xMessage.mappedV2xMsg.put("rsuId",getOs().getId()); //adicionando o rsuId do RSU de acesso ao mapeamento
        //veículo pode ou não ser conhecido pelo RSU
        this.localDynamicVehicleMap.addVehicleInfo(v2xMessage.mappedV2xMsg); //adicionado mesmo se tiver RsuServiceRunner = -1

        vhInfo = connectedVehiclesMap.get(v2xMessage.mappedV2xMsg.get("vhId")); //acessa informações do veículo que enviou a mensagem de serviço
        this.checkVehicleDisconnection(vhInfo);

        registeredRsuServiceRunner = vhInfo.getRsuOfservice(v2xMessage.mappedV2xMsg.get("serviceId")); //descobre o RSU responsável por executar o serviço

        if(Objects.equals(registeredRsuServiceRunner, "-1")){
            //Sem RsuServiceRunner, adicionar na lista de espera e solicitar RsuServiceRenner para o Controlador
            getLog().infoSimTime(this,"No_RSU_Runner: Waiting_List:{}",v2xMessage.mappedV2xMsg.get("msgId"));
            this.waitingServiceMsgMap.put(v2xMessage.mappedV2xMsg.get("msgId"), v2xMessage.getCoreMsg()); //adiciona na fila de espera de execução
            //Log da WaitingList
            //LogUtils.waitingListLog(this, getLog(), this.waitingServiceMsgMap, "rsuRunner_Waiting_List");
            //getLog().infoSimTime(this,"asking_Orchestrator_a_FogComp_to_Vehicle");
            HashMap<String, String> serviceMsgMap = new HashMap<>(v2xMessage.getMappedMsg());
            serviceMsgMap.replace("netDestAddress",rsuConfig.serverNet);
            serviceMsgMap.replace("unitDestId",rsuConfig.serverName);
            MessageRouting routing = getOs().getCellModule().createMessageRouting().protocol(ProtocolType.TCP).
                    topoCast(this.rsuConfig.serverName); //destino é  servidor
            VfnServiceMsg vfnServiceMsg = new VfnServiceMsg(routing,serviceMsgMap);
            applicationsInteractor.sendV2xMsgToApp(vfnServiceMsg, RsuOFSwitchApp.class); //Envia para o switch OpenFlow para o devido encaminhamento


        }else
            if(registeredRsuServiceRunner.contains("rsu_")){
                //RsuServiceRunner já definido, proceder com o processamento (local ou remoto)
                getLog().info("rsuServiceRunner_{} = {}",v2xMessage.mappedV2xMsg.get("serviceId"),registeredRsuServiceRunner); //informa se há RSU registrado para o serviço
                if(Objects.equals(registeredRsuServiceRunner, getOs().getId())){//Processamento em RSU local
                    getLog().infoSimTime(this,"local_processing");
                    this.processResultStr = runService(v2xMessage.mappedV2xMsg); //chamada de módulo de processamento local
                    ///Envio de resultado para o veículo
                    MessageRouting  msgRoute = getOs().getAdHocModule().createMessageRouting().viaChannel(AdHocChannel.CCH).
                            topoCast(v2xMessage.mappedV2xMsg.get("vhId"),this.rsuConfig.defautMsgHops); //resposta para o veículo
                    this.sendResultMsgToVehicle(msgRoute,v2xMessage.mappedV2xMsg);
            } else {
                    //RsuRunner remoto
                    //Neste cenário já se sabe que a rsu é remota
                    getLog().info("remote_RSU:{}", registeredRsuServiceRunner);
                    v2xMessage.mappedV2xMsg.replace("rsuServiceRunner",registeredRsuServiceRunner); //registra a RSU de processamento no corpo da mensagem
                    v2xMessage.mappedV2xMsg.replace("unitDestId",registeredRsuServiceRunner);
                    final MessageRouting msgRoute = getOs().getCellModule().createMessageRouting().protocol(ProtocolType.TCP)
                            .topoCast(registeredRsuServiceRunner); //
                    VfnServiceMsg vfnServiceMsg = new VfnServiceMsg(msgRoute,MsgUtils.getMsgFromStringMap(v2xMessage.mappedV2xMsg)); //VfnServiceMsg extends V2xMessage
                    getLog().infoSimTime(this,"Forwarding_to_{}:{}", registeredRsuServiceRunner, vfnServiceMsg);
                    applicationsInteractor.sendV2xMsgToApp(vfnServiceMsg,RsuOFSwitchApp.class);

            }
        }

    }

    /**
     * Metodo utilizado para lidar com as mensagem recebidas pelo Controlador, como resposta ao pedido de RSU_Runner para a tupla (vehicle,serviceId)
     * ao receber uma mensagem do controlador.
     *  Veículo já está na LDM, então atualizar a informação na LDM e criar evento para que a mensagem seja processada.
     *
     *
     */
    private void controllerMsgHandler(GenericV2xMessage v2xMessage){
        RsuConnectedVehicle connectedVehicle;
        //LogUtils.mappedMsgLog(this,getLog(),v2xMessage.mappedV2xMsg,"MappedMsg-rsuRunnerServiceMsg");
        getLog().infoSimTime(this,"FogComp:{};Vehicle:{}",v2xMessage.getMappedMsg().get("rsuServiceRunner"),v2xMessage.getMappedMsg().get("vhId"));
        connectedVehicle = this.connectedVehiclesMap.get(v2xMessage.mappedV2xMsg.get("vhId")); //acessa referência para o veículo para o qual a mensagem se refere
        connectedVehicle.setRsuServiceRunner(v2xMessage.mappedV2xMsg.get("serviceId"), v2xMessage.mappedV2xMsg.get("rsuServiceRunner")); //adiciona o RSU runner para o serviço
        this.processMsgFromWaitingList(v2xMessage.getMappedMsg()); //método responsável por processar mensagens que aguardavam RSU Runner
    }

    /**
     * Metodo encaminha a mensagem
     * @param v2xMessage
     */
    private void forwardMsgToController(GenericV2xMessage v2xMessage){
        HashMap<String,String> mappedV2xData = new HashMap<>();
        mappedV2xData.putAll(v2xMessage.mappedV2xMsg);
        mappedV2xData.replace("unitDestId", rsuConfig.serverName);
        mappedV2xData.replace("netDestAddress", rsuConfig.serverNet);

        final MessageRouting routing = getOs().getCellModule().createMessageRouting().protocol(ProtocolType.TCP).topoCast(rsuConfig.serverName);

        GenericV2xMessage msgToController = new GenericV2xMessage(routing,mappedV2xData);
        applicationsInteractor.sendV2xMsgToApp(msgToController, RsuOFSwitchApp.class);

    }

    private void camMsgHandler(Cam camMsg){

        byte [] camUserTaggedValue = camMsg.getUserTaggedValue();
        VehicleAwarenessData vehicleAwarenessData = (VehicleAwarenessData) camMsg.getAwarenessData();
        getLog().infoSimTime(this,vehicleAwarenessData.toString());
        //Convert CAM to CarInfoToVfnMsg
        String vehicleStrData;
        vehicleStrData = "#vhId="+camMsg.getRouting().getSource().getSourceName()+
                ";msgType=carBeaconToVFN"+
                ";netDestAddress="+rsuConfig.rsuNet+
                ";unitDestId="+this.getOs().getId()+
                ";latitude=" + String.valueOf(camMsg.getPosition().getLatitude()) +
                ";longitude=" + String.valueOf(camMsg.getPosition().getLongitude()) +
                ";speed=" + String.valueOf(vehicleAwarenessData.getSpeed()) +
                "#";
    }

    /**
     * Como as mensagens são recebidas pela aplicação Switch, o método processEvent tem como finalidade receber as mensagens da aplicação Switch e promover a sua destinação correta
     * @param event
     * @throws Exception
     */
    @Override
    public void processEvent(Event event) throws Exception {
        //getLog().info("Simulation Time: {}",getOs().getSimulationTime());
        Object resource = event.getResource();
        ReceivedV2xMessage receivedV2xMessage;


        if (resource != null) {
            if(resource instanceof RsuConnectedVehicle){ //se já faz mais de 3 segundos que o veículos não informa a sua posição, remove o mesmo da lista de veículos
                RsuConnectedVehicle connectedVehicle = (RsuConnectedVehicle) resource;
                if(this.localDynamicVehicleMap.getVehiclesMap().containsKey(connectedVehicle.getVechicleId())){
                    if((getOs().getSimulationTime()-connectedVehicle.getInfoSentTime())>3*TIME.SECOND){
                        this.localDynamicVehicleMap.getVehiclesMap().remove(connectedVehicle.getVechicleId());
                    }
                    return;
                }

            }
            if (resource instanceof Cam)
            {
                Cam camMsg = (Cam)resource;
                this.camMsgHandler(camMsg);
            }else
            if(resource instanceof GenericV2xMessage){
                this.messageCounter = messageCounter+1;
                GenericV2xMessage v2xMessage = (GenericV2xMessage)resource;

                if(Objects.equals(v2xMessage.getMsgType(), this.rsuConfig.carBeaconType)){
                    //recebimento de mensagem Beacon de veículos - Armazenar na local dynamic map LDM
                    this.carInfoToVfnMsgHandler(v2xMessage);
                    //Forward CarBeaconMessage para o controlador
                    this.forwardMsgToController(v2xMessage);

                }else
                    if(Objects.equals(v2xMessage.getMsgType(), this.rsuConfig.vfnServiceMsgType)) {
                        if(v2xMessage.getRouting().getSource().getSourceName().contains("veh_")){
                            //mensagem provinda de veículo (encaminhada pela APP switch)
                            getLog().infoSimTime(this,"vfnServiceMsgType:{}:{}",v2xMessage.mappedV2xMsg.get("msgId"),v2xMessage.getCoreMsg());
                            this.vehicleServiceMsgHandler(v2xMessage);
                        }else
                            if(v2xMessage.getRouting().getSource().getSourceName().contains("rsu_")){
                            // Mensagem de MicroServiço encaminhada de outra RSU para ser processada nesta
                            getLog().infoSimTime(this,"ForwardedMsg:Source={}:msgId={}:vfnServiceMsgType:{}",v2xMessage.getRouting().getSource().getSourceName(),v2xMessage.mappedV2xMsg.get("msgId"),v2xMessage);
                            this.rsuServiceMsgHandler(v2xMessage);
                        }
                    }else
                        if(Objects.equals(v2xMessage.getMsgType(), this.rsuConfig.rsuRunnerMsgType)) {
                            getLog().infoSimTime(this,"fogComp_decision_from_orchestrator");
                            this.controllerMsgHandler(v2xMessage);
                        }else
                            if(Objects.equals(v2xMessage.getMsgType(), this.rsuConfig.serviceResultMsgType)){
                                //tratamento das mensagem de resposta de microserviço que chegam da rede.
                                getLog().info("Mensagem de resultado deveria ter sido redirecionada no proprio switch e não enviada para o RSU: {} ",v2xMessage.getCoreMsg());

                            }
                //Log DynamicMap content
                //LogUtils.vehicleDMLog(this,getLog(),localDynamicVehicleMap.getVehiclesMap(),"Connected_Vehicles_List");
            }
        }
    }

/*
Método responsável por processsar mensagens que estão aguardando RSU runner na lista de espera.
O método recebe como argumento a mensagem do controlador:
    Se o RSU apontado pelo controlador for o mesmo do RSU recebedor da mensagem provinda do veículo,
        A mensagem deve ser processada localmente.
        Também deve ser removida da lista de espera de processamento
    Se o RSU apontado pelo controlador for diferente do RSU recebedor da mensagem provinda do veículo,
        A mensagem é inserida na lista mensagens encaminhadas
        A mensagem é retirada da lista de espera
        E a mesma mensagem é encaminhada para a RSU remota. ( encaminhar mensagem para o App de interação Celular com a VFN
 */
    public void processMsgFromWaitingList(HashMap<String, String> rsuMsgRunnerMap){

        //extração de informações de mensagens
        String[] splitedVhId = rsuMsgRunnerMap.get("vhId").split("_");
        String[] splitedServiceId = rsuMsgRunnerMap.get("serviceId").split("_");
        String[] splitedRunnerRsuId = rsuMsgRunnerMap.get("rsuServiceRunner").split("_"); //RSU que será usado no processamento
        String vhServiceCodeFromController = splitedVhId[1] + "_" + splitedServiceId[1]; //código no formato vhId_serviceId (Ex. 1_01)

        String resultOfProcess="";
        HashMap<String,String> serviceMsgMap; //map que armazena os dados da mensagem da lista que será processada

        getLog().infoSimTime(this,"vhServiceRsuCode_wait-list= {}",vhServiceCodeFromController);
        ArrayList<String> keysToRemove = new ArrayList<>(); //cria uma lista de mensagens aptas a serem removidas após processamento

        //Busca na WaitingList para processar mensagens aptas
        for(Map.Entry<String, String> keyEntry : this.waitingServiceMsgMap.entrySet()){
            String[] splitedMsgKey = keyEntry.getKey().split("_");
            String vhServiceCodeFromWList = splitedMsgKey[0]+"_"+splitedMsgKey[1]; //extrai código comparável para saber se a mensagem será processada

            //se os codigos de veículo e serviço fazem match, realizar o processamento
            if(vhServiceCodeFromController.equals(vhServiceCodeFromWList)){
                //mapea mensagem que será processada
                serviceMsgMap = MsgUtils.extractMsgToHash(keyEntry.getValue());
                serviceMsgMap.replace("rsuServiceRunner",rsuMsgRunnerMap.get("rsuServiceRunner")); //Atualiza o rsuServiceRunner na mensagem a ser processada
                keysToRemove.add(keyEntry.getKey()); //adiciona chave da mensagem para remoção da waitingList

                //analisar se o processamento é local ou se a mensagem deve ser encaminhada
                if(Objects.equals(serviceMsgMap.get("rsuServiceRunner"), getOs().getId())){//processamento local
                    //neste caso, o rsuRunner enviado pelo controlador é a própria Rsu AccessPoint
                    this.processResultStr=this.runService(serviceMsgMap); //processa e recebe o resultado do processamento
                    MessageRouting  msgRoute = getOs().getAdHocModule().createMessageRouting().viaChannel(AdHocChannel.CCH).topoCast(serviceMsgMap.get("vhId"),this.rsuConfig.defautMsgHops);
                    this.sendResultMsgToVehicle(msgRoute,serviceMsgMap); //envio de mensagem de volta ao veículo remetente


                }else{
                    //necessita encaminhar a mensagem para rsuRunner remoto
                    serviceMsgMap.replace("unitDestId",rsuMsgRunnerMap.get("rsuServiceRunner"));
                    getLog().infoSimTime(this,"vfnServiceMsg_to_forward: {}",serviceMsgMap.toString());
                    final MessageRouting routing = getOs().getCellModule().createMessageRouting().protocol(ProtocolType.TCP)
                            .topoCast(serviceMsgMap.get("rsuServiceRunner"));
                    GenericV2xMessage vfnServiceMsg = new GenericV2xMessage(routing,serviceMsgMap); //VfnServiceMsg
                    this.applicationsInteractor.sendV2xMsgToApp(vfnServiceMsg, RsuOFSwitchApp.class);
                }

            }
        }
        for (String keytoRemove : keysToRemove){
            this.waitingServiceMsgMap.remove(keytoRemove);
        }
}

    /**
     * O método runService realiza a execução do serviço consumido pelo veículo
     * @param mappedMsg
     * @return
     */
    public String runService(HashMap<String, String> mappedMsg){
        //processar mensagem do MicroServiço
        int serviceResult = 0;
        //código para execução do Microserviço
        if(Objects.equals(mappedMsg.get("serviceId"), "service_01")){
            String[] splitedServiceData = mappedMsg.get("serviceMsg").split("&");
            //getLog().info("message to process: {} and {}", splitedServiceData[0], splitedServiceData[1]);
            String[] termValue1 = splitedServiceData[0].split(":");
            Integer term1 = Integer.valueOf(termValue1[1]);
            String[] termValue2 = splitedServiceData[1].split(":");
            Integer term2 = Integer.valueOf(termValue2[1]);
            serviceResult = term1+term2;

            //getLog().info("Result of the process: {}",serviceResult);
        }else{
            System.out.println("problem on processing service");
        }
        //if(serviceResult==135){
            //System.out.println("depurar");
        //}
        return String.valueOf(serviceResult);

    }


    /**
     * Método de encaminhameno de mensagem para outro RSU. Cria uma mensagem para o destino final.
     * Não considera os saltos entre os nós de origem e destino.
     * @param mappedServiceMsg
     */
    public void forwardServiceMsg(HashMap<String,String> mappedServiceMsg){
        String  serviceCoreMsg = MsgUtils.getMsgFromStringMap(mappedServiceMsg);
        final MessageRouting msgRoute = getOs().getCellModule().createMessageRouting()
                .protocol(ProtocolType.TCP)
                .topoCast(mappedServiceMsg.get("rsuServiceRunner"));
        VfnServiceMsg vfnServiceMsg = new VfnServiceMsg(msgRoute,serviceCoreMsg); //VfnServiceMsg extends V2xMessage
        //enviar mensagem para a aplicação de troca de mensagens com a rede Celular para encaminhamento
        getLog().infoSimTime(this,"rsuServiceRunner_forward_to: {}:{}", mappedServiceMsg.get("rsuServiceRunner"), vfnServiceMsg);
        applicationsInteractor.sendV2xMsgToApp(vfnServiceMsg,RsuOFSwitchApp.class);
        //gera encaminhamento de mensagem e aguarda recebimento
    }


    /**
     * Ações a serem executadas durante a finalização da aplicação
     */
    @Override
    public void onShutdown()
    {

        getLog().infoSimTime(this, "Final state of LocalDynamicMap");
        HashMap<String, RsuConnectedVehicle> myMap = localDynamicVehicleMap.getVehiclesMap();
        for (String key: myMap.keySet()){
            RsuConnectedVehicle vehicleInfo = myMap.get(key);
            getLog().info(key+" = " +vehicleInfo.toString());
        }
        getLog().infoSimTime(this, "Final de aplicação");




    }
}


