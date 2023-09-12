
package org.eclipse.mosaic.app.sdnvfn;

import org.eclipse.mosaic.app.sdnvfn.information.DynamicVehicleMap;
import org.eclipse.mosaic.app.sdnvfn.information.RsuConnectedVehicle;
import org.eclipse.mosaic.app.sdnvfn.message.*;
import org.eclipse.mosaic.app.sdnvfn.utils.LogUtils;
import org.eclipse.mosaic.app.sdnvfn.utils.MsgUtils;
import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.ReceivedV2xMessage;
import org.eclipse.mosaic.fed.application.app.AbstractApplication;
import org.eclipse.mosaic.fed.application.app.api.Application;
import org.eclipse.mosaic.fed.application.app.api.os.RoadSideUnitOperatingSystem;
import org.eclipse.mosaic.lib.enums.AdHocChannel;
import org.eclipse.mosaic.lib.enums.ProtocolType;
import org.eclipse.mosaic.lib.objects.v2x.MessageRouting;
import org.eclipse.mosaic.lib.objects.v2x.V2xMessage;
import org.eclipse.mosaic.lib.util.scheduling.Event;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.InetAddress;
import java.util.*;

/**
 * Class used as a Knowledge Base to the RSU.
 */
public  class RsuKnowledgeBaseApp extends AbstractApplication<RoadSideUnitOperatingSystem> {

    private DynamicVehicleMap localDynamicVehicleMap;
    private HashMap<String, RsuConnectedVehicle> connectedVehiclesMap;
    private List<? extends Application> applicationList;
    private HashMap<String,Application> appMap;

    private HashMap<String,String> waitingServiceMsgMap;
    private HashMap<String,String> forwardedMsgMap;
    //private HashMap<String,String> mappedServiceMsg;
    private String processResultStr;
    public int messageCounter;


    @Override
    public void onStartup() {
        getLog().infoSimTime(this, "Enabling RsuKnowledgeBase in {} ",getOs().getId());
        this.localDynamicVehicleMap = new DynamicVehicleMap();
        this.applicationList = getOs().getApplications();
        this.appMap = new HashMap<>();
        this.convertAppListToMap();
        this.waitingServiceMsgMap = new HashMap<>();  //inicia lista de mensagens em espera
        this.forwardedMsgMap = new HashMap<>(); //inicia lista de mensagens encaminhadas
        this.connectedVehiclesMap = localDynamicVehicleMap.getVehiclesMap(); //acessa o mapa de veiculos de suas informações
        this.messageCounter = 0;


    }

    /**
     * Método encaminha mensagem de resposta de serviço da RSU para o veículo solicitante que esteja conectado na RSU atual
     */
    private void dispachServiceResultMsgToApp(MessageRouting msgRoute, String serviceProcessResult, HashMap<String,String> mappedServiceMsg, Class appClass){
        HashMap<String,String> mappedResultMsg = new HashMap<>();
        mappedResultMsg.put("msgType","serviceProcessResult");
        mappedResultMsg.put("serviceProcessResult",serviceProcessResult);
        mappedResultMsg.put("vhId",mappedServiceMsg.get("vhId"));
        mappedResultMsg.put("msgId",mappedServiceMsg.get("msgId"));

        final VfnServiceResultMsg serviceResultV2xMsg;
        serviceResultV2xMsg = new VfnServiceResultMsg(msgRoute, MsgUtils.getMsgFromStringMap(mappedResultMsg));
        final Event event = new Event(getOs().getSimulationTime(),this.appMap.get(appClass.getName()), serviceResultV2xMsg);
        this.getOs().getEventManager().addEvent(event);
    }

    private void carInfoToVfnMsgHandler(CarInfoToVfnMsg carInfoToVfnMsg){
        //Ações a serem realizadas ao receber uma mensagem de beacom de veículos
        HashMap<String, String> carInfoMsgMap;
        carInfoMsgMap = MsgUtils.extractMsgToHash(carInfoToVfnMsg.toString());
        getLog().infoSimTime(this,"Received Message: {}",carInfoToVfnMsg.toString());
        carInfoMsgMap.put("rsuId",getOs().getId()); //adicionando o rsuId do RSU de acesso ao mapeamento

        getLog().info("---------------------------MappedMsg-CarInfoToVfnMsg---------------------");
        for(Map.Entry<String, String> keyEntry : carInfoMsgMap.entrySet()){
            getLog().info("{} = {}",keyEntry.getKey(), keyEntry.getValue());
        }
        getLog().info("-------------------------------------------------");

        this.localDynamicVehicleMap.addVehicleInfo(carInfoMsgMap);
    }

    private void logMappedMsgContent(VfnServiceMsg vfnServiceMsg, HashMap<String,String> mappedServiceMsg, String title){

        getLog().infoSimTime(this,"Received {} Message from {}: {}",title, vfnServiceMsg.getRouting().getSource().getSourceName(),vfnServiceMsg.toString());
        getLog().info("rsuServiceRunner que chegou com a mensagem:{}",mappedServiceMsg.get("rsuServiceRunner"));
        LogUtils.mappedMsgLog(this,getLog(),mappedServiceMsg,"MappedMsg-VfnServiceMsg");
        getLog().info("---------------------------MappedMsg-VfnServiceMsg---------------------");
        for(Map.Entry<String, String> keyEntry : mappedServiceMsg.entrySet()){
            getLog().info("{} = {}",keyEntry.getKey(), keyEntry.getValue());
        }
        getLog().info("----------------------------------------------------");
    }

    private void rsuServiceMsgHandler(VfnServiceMsg vfnServiceMsg){
        //Mensagem vinda de RSU por encaminhamento
        //RSU de processamento consta na mensagem
        getLog().infoSimTime(this,"Received {} Message from {}: {}",VfnServiceMsg.class.getName(), vfnServiceMsg.getRouting().getSource().getSourceName(),vfnServiceMsg.toString());
        HashMap<String,String> mappedServiceMsg = MsgUtils.extractMsgToHash(vfnServiceMsg.toString());
        //getLog().info("rsuServiceRunner que chegou com a mensagem:{}",mappedServiceMsg.get("rsuServiceRunner"));
        //this.logMappedMsgContent(vfnServiceMsg, mappedServiceMsg,"VfnServiceMsg"); //mapea a mensagem e realiza log de recebimento
        //LogUtils.mappedMsgLog(this,getLog(),mappedServiceMsg,VfnServiceMsg.class.getName());
        localDynamicVehicleMap.addVehicleInfo(mappedServiceMsg); //atualiza os dados do veículo na LDM
        //neste momento os dados do veículo são atualizados na RSU que recebe a mensagem de processamento.
        this.processResultStr =  runService(mappedServiceMsg);
        getLog().info("Send result to RSU witch the vehicle {} is connected: {}",mappedServiceMsg.get("vhId"), mappedServiceMsg.get("rsuId"));
        final MessageRouting msgRoute = getOs().getCellModule().createMessageRouting().protocol(ProtocolType.TCP).topoCast(vfnServiceMsg.getRouting().getSource().getSourceName());
        this.dispachServiceResultMsgToApp(msgRoute,this.processResultStr,mappedServiceMsg,RsuToVfnMsgExchangeApp.class); //envio de mensagem de volta ao remetente RSU
    }

    /**
     * Este método se destina a tratar mensagens de serviço vindas de veículos situados na rede Ad-hoc
     * As mensagens são extraídas
     * @param vfnServiceMsg
     */
    private void vehicleServiceMsgHandler(VfnServiceMsg vfnServiceMsg){
        RsuConnectedVehicle vhInfo;
        String registeredRsuServiceRunner = "";
        HashMap<String,String> mappedServiceMsg = MsgUtils.extractMsgToHash(vfnServiceMsg.toString());
        //Nova mensagem vinda de veículos
        //LogUtils.mappedMsgLog(this,getLog(),mappedServiceMsg,VfnServiceMsg.class.getName());
        //this.logMappedMsgContent(vfnServiceMsg, mappedServiceMsg,"VfnServiceMsg");
        //veículo pode ou não ser conhecido pelo RSU
        mappedServiceMsg.put("rsuId",getOs().getId()); //adicionando o rsuId do RSU de acesso ao mapeamento
        this.localDynamicVehicleMap.addVehicleInfo(mappedServiceMsg); //adicionado com RSU = -1
        vhInfo = connectedVehiclesMap.get(mappedServiceMsg.get("vhId")); //acessa informações do veículo que enviou a mensagem de serviço
        getLog().info("Vehicle Data on LDM: {}", vhInfo.toString());

        registeredRsuServiceRunner = vhInfo.getRsuOfservice(mappedServiceMsg.get("serviceId")); //descobre o RSU responsável por executar o serviço

        if(Objects.equals(registeredRsuServiceRunner, "-1")){
            //Se não houver registro de RSU pra o serviço deste veículo,
            // adicionar a mensagem a uma fila de processamento e
            // encaminhar mensagem para o Controlador
            //não havia registro de RSU runner para o serviço do veículo conectado
            getLog().info("Serviço sem rsuRunner: Adicionando na lista de Espera");
            this.waitingServiceMsgMap.put(mappedServiceMsg.get("msgId"),vfnServiceMsg.getCoreMsg()); //adiciona na fila de espera de execução

            getLog().info("Encaminhando mensagem para o Controlador");
            //Mostra a WaitingList após o processamento
            getLog().info("---------------Lista de Espera por um rsuRunner-----------------");
            for(Map.Entry<String, String> keyEntry : this.waitingServiceMsgMap.entrySet()){
                getLog().info("{} = {}",keyEntry.getKey(), keyEntry.getValue());
            }
            this.dispachServiceMsgToApp(vfnServiceMsg,RsuToVfnMsgExchangeApp.class); //solicita ao controlador os dados sobre o serviço

        }else  if(registeredRsuServiceRunner.contains("rsu_")){
            //LDM já foi atualizada pelo controlador com o RsuRunner para a tupla (veículo,serviço)
            //Já pode haver processamento (local ou remoto)
            getLog().info("rsuServiceRunner for {} = {}",mappedServiceMsg.get("serviceId"),registeredRsuServiceRunner); //informa se há RSU registrado para o serviço

            if(Objects.equals(registeredRsuServiceRunner, getOs().getId())){
                //rsu local
                getLog().info("Processamento será na RSU Local");
                //Caso a RSU de execução for a atual, chamar método de execução de Serviço
                //System.out.println("EXECUTAR AQUI");
                this.processResultStr = runService(mappedServiceMsg);
                //após o processamento, enviar a confirmação para o veículo. Àquele que enviou a mensagem
                MessageRouting  msgRoute = getOs().getAdHocModule().createMessageRouting().viaChannel(AdHocChannel.CCH).topoCast(mappedServiceMsg.get("vhId"),5);
                this.dispachServiceResultMsgToApp(msgRoute,this.processResultStr,mappedServiceMsg,RsuAdHocMsgExchangeApp.class); //envio de mensagem de volta ao remetente

            } else {
                //rsu remota
                //Neste cenário já se sabe que a rsu é remota
                //adicionar em Lista de mensagens encaminhadas
                getLog().info("Mensagem será executada na RSU remota:{}", registeredRsuServiceRunner);
                mappedServiceMsg.replace("rsuServiceRunner",registeredRsuServiceRunner); //registra a RSU de processamento no corpo da mensagem
                forwardServiceMsg(mappedServiceMsg); //store in fwtable and send do vfn msg handle APP.

            }
        }

    }

    /**
     * Metodo utilizado para lidar com as mensagem recebidas pelo RSU, como resposta ao pedido de RSU_Runner para a tupla (vehicle,serviceId)
     * ao receber uma mensagem do controlador.
     *  Veículo já está na LDM, então atualizar a informação na LDM e criar evento para que a mensagem seja processada.
     *
     *
     */
    private void controllerMsgHandler(ReceivedV2xMessage receivedV2xMessage){
        ControllerServerMsg serviceRunnerControllerMsg = (ControllerServerMsg) receivedV2xMessage.getMessage();
        RsuConnectedVehicle vhInfo;

        HashMap<String,String> mappedControlerServiceRunnerMsg = MsgUtils.extractMsgToHash(serviceRunnerControllerMsg.toString());
        getLog().infoSimTime(this,"Received Message: {}",serviceRunnerControllerMsg.toString());
        LogUtils.mappedMsgLog(this,getLog(),mappedControlerServiceRunnerMsg,"MappedMsg-ServiceRunnerMsg");
        //imprimindo a mensagem mapeada
        /**
         *  getLog().info("---------------------------MappedMsg-ServiceRunnerMsg---------------------");
         *         for(Map.Entry<String, String> keyEntry : mappedControlerServiceRunnerMsg.entrySet()){
         *             getLog().info("{} = {}",keyEntry.getKey(), keyEntry.getValue());
         *         }
         *         getLog().info("-------------------------------------------------");
         */


        vhInfo = this.connectedVehiclesMap.get(mappedControlerServiceRunnerMsg.get("vhId")); //acessa referência para o veículo para o qual a mensagem se refere
        vhInfo.setRsuServiceRunner(mappedControlerServiceRunnerMsg.get("serviceId"), mappedControlerServiceRunnerMsg.get("rsuServiceRunner")); //adiciona o RSU runner para o serviço
        this.processMsgFromWaitingList(mappedControlerServiceRunnerMsg); //método responsável por processar mensagens que aguardavam RSU Runner
    }



    @Override
    public void processEvent(Event event) throws Exception {
        getLog().info("Simulation Time: {}",getOs().getSimulationTime());
        Object resource = event.getResource();
        ReceivedV2xMessage receivedV2xMessage;
        if (resource != null) {
            if (resource instanceof ReceivedV2xMessage) {
                this.messageCounter = messageCounter+1;
                //getLog().info("Contador de mensagem:{}", this.messageCounter);
                receivedV2xMessage = (ReceivedV2xMessage) resource;
                if(receivedV2xMessage.getMessage() instanceof CarInfoToVfnMsg){
                    //recebimento de mensagem Beacon de veículos
                    this.carInfoToVfnMsgHandler((CarInfoToVfnMsg) receivedV2xMessage.getMessage());
                }else if(receivedV2xMessage.getMessage() instanceof VfnServiceMsg){
                    if(receivedV2xMessage.getMessage().getRouting().getSource().getSourceName().contains("veh_")){
                        //mensagem provinda de veículo
                        getLog().info("Mensagem recebida a ser tratada na Knowledge Base");
                        this.vehicleServiceMsgHandler((VfnServiceMsg) receivedV2xMessage.getMessage());
                    }else if(receivedV2xMessage.getMessage().getRouting().getSource().getSourceName().contains("rsu_")){
                        //mensagem provinda de outra RSU
                        getLog().info("Message arrived from: {} --- {}",receivedV2xMessage.getMessage().getRouting().getSource().getSourceName(),receivedV2xMessage.getMessage());
                        this.rsuServiceMsgHandler((VfnServiceMsg)  receivedV2xMessage.getMessage());
                    }

                } else if(receivedV2xMessage.getMessage() instanceof ControllerServerMsg){
                    getLog().info("Arrived ServiceRunnerMsg");
                    this.controllerMsgHandler(receivedV2xMessage);
                    }else if(receivedV2xMessage.getMessage() instanceof VfnServiceResultMsg){
                        //remover da lista de encaminhadas
                        //Temporariamente não utilizado - Lista de forward desativada
                        getLog().info("Arrived Remote Result Message: {} ",((VfnServiceResultMsg) receivedV2xMessage.getMessage()).getCoreMsg());
                        HashMap<String, String> mappedResultMsg = MsgUtils.extractMsgToHash(((VfnServiceResultMsg) receivedV2xMessage.getMessage()).getCoreMsg());
                        this.forwardedMsgMap.remove(mappedResultMsg.get("msgId"));
                        LogUtils.mappedMsgLog(this,getLog(),this.forwardedMsgMap,"Forwarded Messages waiting for an answer");
                    }


                getLog().info("--------------- Connected Vehicles List");
                this.LogContentOfDymanicMap(localDynamicVehicleMap.getVehiclesMap());
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

        getLog().info("vhServiceRsuCode a ser processado = {}",vhServiceCodeFromController);
        ArrayList<String> keysToRemove = new ArrayList<>(); //cria uma lista de mensagens aptas a serem removidas após processamento

        //Busca na WaitingList para processar mensagens aptas
        for(Map.Entry<String, String> keyEntry : this.waitingServiceMsgMap.entrySet()){
            String[] splitedMsgKey = keyEntry.getKey().split("_");
            String vhServiceCodeFromWList = splitedMsgKey[0]+"_"+splitedMsgKey[1]; //extrai código comparável para saber se a mensagem será processada
            getLog().info("VhServiceRsuKey = {}",vhServiceCodeFromWList);

            //se os codigos de veículo e serviço fazem match, realizar o processamento
            if(vhServiceCodeFromController.equals(vhServiceCodeFromWList)){
                //mapea mensagem que será processada
                serviceMsgMap = MsgUtils.extractMsgToHash(keyEntry.getValue());
                serviceMsgMap.replace("rsuServiceRunner",rsuMsgRunnerMap.get("rsuServiceRunner")); //Atualiza o rsuServiceRunner na mensagem a ser processada
                keysToRemove.add(keyEntry.getKey()); //adiciona chave da mensagem para remoção da waitingList

                //analisar se o processamento é local ou se a mensagem deve ser encaminhada (Se RSU runner forem iguais)
                if(Objects.equals(serviceMsgMap.get("rsuServiceRunner"), getOs().getId())){
                    //neste caso, o rsuRunner enviado pelo controlador é a própria Rsu AccessPoint
                    //processamento local
                    this.processResultStr=this.runService(serviceMsgMap); //processa e recebe o resultado do processamento
                    MessageRouting  msgRoute = getOs().getAdHocModule().createMessageRouting().viaChannel(AdHocChannel.CCH).topoCast(serviceMsgMap.get("vhId"),5);
                    this.dispachServiceResultMsgToApp(msgRoute,this.processResultStr,serviceMsgMap,RsuAdHocMsgExchangeApp.class); //envio de mensagem de volta ao remetente


                }else{
                    //necessita encaminhar a mensagem para rsuRunner remoto
                    String strFwMsgData = getStrFwDataFromServiceMsgMap(serviceMsgMap);
                    getLog().info("Mensagem a ser encaminhada: {}",strFwMsgData);
                    final MessageRouting routing = getOs().getCellModule().createMessageRouting().protocol(ProtocolType.TCP).topoCast(rsuMsgRunnerMap.get("rsuServiceRunner"));
                    VfnServiceMsg forwardedServiceMsg = new VfnServiceMsg(routing,strFwMsgData); //VfnServiceMsg
                    this.dispachForwardedMsgToApp(forwardedServiceMsg, RsuToVfnMsgExchangeApp.class); //Envia para a aplicação de

                    this.forwardedMsgMap.put(keyEntry.getKey(),strFwMsgData); //insere mensagem atual na lista(map) de encaminhados
                    //keysToRemove.add(keyEntry.getKey()); //Registra a mensagem para ser removida

                }

            }
        }
        for (String keytoRemove : keysToRemove){
            this.waitingServiceMsgMap.remove(keytoRemove);
        }
}
    public String getStrFwDataFromServiceMsgMap(HashMap<String,String> vehicleMsgMap){
        String serviceStrData;
        serviceStrData =
                "#vhId="+ vehicleMsgMap.get("vhId")+
                        ";latitude="+ vehicleMsgMap.get("latitude")+
                        ";longitude="+ vehicleMsgMap.get("longitude")+
                        ";speed="+ vehicleMsgMap.get("speed")+
                        ";serviceId="+ vehicleMsgMap.get("serviceId")+
                        ";msgId="+ vehicleMsgMap.get("msgId")+
                        ";rsuServiceRunner="+ vehicleMsgMap.get("rsuServiceRunner")+
                        ";serviceMsg="+vehicleMsgMap.get("serviceMsg")+
                        "#";
        return serviceStrData;
    }

    public void LogContentOfDymanicMap(HashMap<String, RsuConnectedVehicle> vehiclesMap){
    for (String key: vehiclesMap.keySet()){
        RsuConnectedVehicle vehicleInfo = vehiclesMap.get(key);
        getLog().info(key+" = " +vehicleInfo.toString());
    }
    getLog().info("----------------------------------------------\n\n\n");
}

    @Override
    public void onShutdown()
    {

        getLog().infoSimTime(this, "Final state of LocalDynamicMap");
        HashMap<String, RsuConnectedVehicle> myMap = localDynamicVehicleMap.getVehiclesMap();
        for (String key: myMap.keySet()){
            RsuConnectedVehicle vehicleInfo = myMap.get(key);
            getLog().info(key+" = " +vehicleInfo.toString());
        }
        getLog().infoSimTime(this, "Tear down");




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

    public void dispachMsgToApp(@NotNull V2xMessage myV2xMessage, Class appClass){
        //Comunicando com outra aplicação via EventProcess
        final Event sendIntraUnitMsg = new Event(getOs().getSimulationTime(),this.appMap.get(appClass.getName()),myV2xMessage);
        this.getOs().getEventManager().addEvent(sendIntraUnitMsg);
    }

    public void dispachServiceMsgToApp(@NotNull VfnServiceMsg myServiceMessage, Class appClass){
        //Comunicando com outra aplicação via EventProcess
        final Event sendServiceMsg = new Event(getOs().getSimulationTime(),this.appMap.get(appClass.getName()),myServiceMessage);
        this.getOs().getEventManager().addEvent(sendServiceMsg);
    }

    public void dispachForwardedMsgToApp(@NotNull VfnServiceMsg forwardedServiceMsg, Class appClass){
        //Comunicando com outra aplicação via EventProcess
        final Event sendIntraUnitMsg = new Event(getOs().getSimulationTime(),this.appMap.get(appClass.getName()),forwardedServiceMsg);
        this.getOs().getEventManager().addEvent(sendIntraUnitMsg);
    }
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

        return String.valueOf(serviceResult);

    }


    public void forwardServiceMsg(HashMap<String,String> mappedServiceMsg){
        String  serviceCoreMsg = MsgUtils.getMsgFromStringMap(mappedServiceMsg);
        final MessageRouting msgRoute = getOs().getCellModule().createMessageRouting()
                .protocol(ProtocolType.TCP)
                .topoCast(mappedServiceMsg.get("rsuServiceRunner"));
        VfnServiceMsg vfnServiceMsg = new VfnServiceMsg(msgRoute,serviceCoreMsg); //VfnServiceMsg extends V2xMessage
        //this.forwardedMsgMap.put(mappedServiceMsg.get("msgId"), vfnServiceMsg.toString()); //adiciona à lista de encaminhados.

        //this.logMappedMsg(this.forwardedMsgMap,"ForwardedMsgMap - Lista de encaminhadas"); //show fowardList

        //enviar mensagem para a aplicação de troca de mensagens com a rede Celular para encaminhamento
        getLog().info("Encaminhando mensagem para {}:{}", mappedServiceMsg.get("rsuServiceRunner"), vfnServiceMsg);

        this.dispachServiceMsgToApp(vfnServiceMsg, RsuToVfnMsgExchangeApp.class);
        //gera encaminhamento de mensagem e aguarda recebimento
    }
}
