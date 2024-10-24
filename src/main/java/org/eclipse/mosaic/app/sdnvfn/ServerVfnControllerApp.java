package org.eclipse.mosaic.app.sdnvfn;

import org.eclipse.mosaic.app.sdnvfn.config.ServerConfig;
import org.eclipse.mosaic.app.sdnvfn.information.GlobalDynamicVehicleMap;
import org.eclipse.mosaic.app.sdnvfn.information.PriorityConnectedRsuList;
import org.eclipse.mosaic.app.sdnvfn.information.VfnConnectedVehicle;
import org.eclipse.mosaic.app.sdnvfn.message.ControllerServerMsg;
import org.eclipse.mosaic.app.sdnvfn.message.GenericV2xMessage;
import org.eclipse.mosaic.app.sdnvfn.network.*;
import org.eclipse.mosaic.app.sdnvfn.utils.LogUtils;
import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.CamBuilder;
import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.ReceivedAcknowledgement;
import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.ReceivedV2xMessage;
import org.eclipse.mosaic.fed.application.app.ConfigurableApplication;
import org.eclipse.mosaic.fed.application.app.api.os.ServerOperatingSystem;
import org.eclipse.mosaic.fed.application.app.api.CommunicationApplication;
import org.eclipse.mosaic.interactions.communication.V2xMessageTransmission;
import org.eclipse.mosaic.lib.enums.ProtocolType;
import org.eclipse.mosaic.lib.geo.MutableGeoPoint;
import org.eclipse.mosaic.lib.objects.v2x.MessageRouting;
import org.eclipse.mosaic.lib.util.scheduling.Event;
import org.eclipse.mosaic.rti.TIME;

import java.util.*;

public class ServerVfnControllerApp extends ConfigurableApplication<ServerConfig, ServerOperatingSystem> implements CommunicationApplication {

    private GlobalDynamicVehicleMap globalDynamicMap;
    private CommunicationInterface communicationInterface;
    private ServerConfig srvConfig;
    private SdnController sdnController;
    private HashMap<String,MutableGeoPoint> rsuPositionsMap;
    private Map<String, NetworkNode> rsuNodesMap;
    private RsuPredictor rsuPredictor;
    private NextRsuSelector nextRsuSelector;
    private int currentIndexFogComp;

    public ServerVfnControllerApp() {
        super(ServerConfig.class, "ServerConfiguration");
    }


    @Override
    public void onStartup() {
        currentIndexFogComp =0; //Inicia selecionando o primeiro fog Node da lista
        //getLog().infoSimTime(this, "Initialize ServerVfnControllerApp application");
        this.srvConfig = this.getConfiguration();  //load ConfigFile
        //getLog().info("Common_ServiceList:{}", srvConfig.commonServiceList);
        //getLog().info("Specific_ServiceList:{}", srvConfig.specificServiceList);

        communicationInterface = new CommunicationInterface(this);
        communicationInterface.createCellInterface();

        //The Global Dynamic Map extends generic DynamicMap. It needs the commonServiceList and the specificServiceList.
        globalDynamicMap = new GlobalDynamicVehicleMap(srvConfig.commonServiceList, srvConfig.specificServiceList);

        this.rsuPositionsMap =this.mappingRsuPositions(srvConfig.rsusPositions);
        this.sdnController = new SdnController(this.srvConfig.rsusConnections,rsuPositionsMap,this,getLog(),this.srvConfig);
        this.rsuNodesMap = new HashMap<>();


        //Criação do Grafo da Topologia da Rede
        NetworkNode rsuNetNode;
        for (Map.Entry<String,MutableGeoPoint> entry: this.rsuPositionsMap.entrySet()) {
            rsuNetNode = sdnController.getNetworkTopology().getNetNode(entry.getKey());
            rsuNodesMap.put(entry.getKey(),rsuNetNode);
            //getLog().info(rsuNodesMap.get(entry.getKey()).printNode());
        }
        rsuPredictor = new RsuPredictor(this.rsuPositionsMap, this.srvConfig); //instanciação do módulo RsuPredictor
        nextRsuSelector = new NextRsuSelector(this.rsuPositionsMap,srvConfig);

    }

    private HashMap<String,MutableGeoPoint> mappingRsuPositions(ArrayList<String> rsuPositionList){
        String[] pairRsuPositions;
        String[] rsuLatLong;
        HashMap<String, MutableGeoPoint> mapRsuPositions = new HashMap<>();
        for(String rsuPosStr: rsuPositionList){
            pairRsuPositions = rsuPosStr.split(":");
            rsuLatLong = pairRsuPositions[1].split(",");
            mapRsuPositions.put(pairRsuPositions[0],new MutableGeoPoint(Double.parseDouble(rsuLatLong[0]),Double.parseDouble(rsuLatLong[1])));
        }
        return mapRsuPositions;
    }

    @Override
    public void processEvent(Event event) throws Exception {
        //event to process
    }



    /**
     * Este método envia regras OpenFlow para que não ocorra packetIn no próximo RSU
     * Para cada serviço ativo do veículo, envia regras baseadas em Veiculo/serviço e regras baseadas em origem destino.
     * Regras veículo/serviço tem prioridade sobre regras tipo origem/destino
     * @param vfnConnectedVehicle Objeto que detem os dados do veículo conectado à VFN
     */
    public void sendRuleToPredictedRsu(VfnConnectedVehicle vfnConnectedVehicle){
        for(Map.Entry<String,String> serviceRsuPair: vfnConnectedVehicle.getServiceRsuMap().entrySet()){ //Como só trabalhamos com 1 serviço, quer dizer que podemos
            if(!Objects.equals(serviceRsuPair.getValue(), "unused")){
                ArrayList<String> pathToRunnerArray = sdnController.getStrPathToRsu(vfnConnectedVehicle.getNextRsuId(),serviceRsuPair.getValue());
                sdnController.sendVehicleServiceRuleToRsuSwitch(
                        pathToRunnerArray,
                        vfnConnectedVehicle.getVechicleId(),serviceRsuPair.getKey(),serviceRsuPair.getValue());
            }
        }
        ArrayList<String> pathToRunnerArray = sdnController.getStrPathToRsu(vfnConnectedVehicle.getNextRsuId(),vfnConnectedVehicle.getRsuOfservice(srvConfig.commonServiceList.get(0)));
        sdnController.sendRuleToRsuSwitch(pathToRunnerArray);

    }

    /**
     * O método recebe mensagens de veículos provindas de RSUs.
     * Tipos de mensagem: Beacon de Veículos, Consumo de  Serviço, Mensagens OpenFlow do RSU
     * @param receivedV2xMessage Mensagem enviadas por RSUs.
     */
    @Override
    public void onMessageReceived(ReceivedV2xMessage receivedV2xMessage) {
        getLog().infoSimTime(this, "Received_Message: {}",receivedV2xMessage.getMessage().toString()); // Imprime a mensagem recebida
        GenericV2xMessage v2xMessage = (GenericV2xMessage) receivedV2xMessage.getMessage();

        if(Objects.equals(v2xMessage.getMsgType(), this.srvConfig.openFlowMsgType)){
            //Mensagens OpenFlow
            String rsuRunnerOfService;


            getLog().infoSimTime(this,"PacketIn:{}",v2xMessage.getCoreMsg());
            if(Objects.equals(v2xMessage.mappedV2xMsg.get("ofMsg"), srvConfig.openFlowPacketInMsg)){
                ArrayList<String> pathToRunnerArray = sdnController.getStrPathToRsu(v2xMessage.getRsuAPId(),v2xMessage.getUnitDestId());
                if(globalDynamicMap.getVfnVehiclesMap().containsKey(v2xMessage.getMappedMsg().get("vhId"))){
                    rsuRunnerOfService = globalDynamicMap.getVfnVehiclesMap().get(v2xMessage.getMappedMsg().get("vhId")).getRsuOfservice(v2xMessage.getMappedMsg().get("serviceId"));
                    sdnController.sendVehicleServiceRuleToRsuSwitch(pathToRunnerArray,v2xMessage.getMappedMsg().get("vhId"),v2xMessage.getMappedMsg().get("serviceId"),rsuRunnerOfService);//envio de regras baseados em vehicle/service origem e RSU destino.
                }
                sdnController.sendRuleToRsuSwitch(pathToRunnerArray);
                //Devolve a mensagem para o rsu que enviou
                sdnController.sendPacketOut(v2xMessage);
            }

        }else{

            if(Objects.equals(v2xMessage.getMsgType(), this.srvConfig.vfnServiceMsgType) || Objects.equals(v2xMessage.getMsgType(), this.srvConfig.carBeaconType)){
                vehicleMsgHandle(v2xMessage);
            }

        }
    }

    public void vehicleMsgHandle(GenericV2xMessage v2xMessage){
        String rsuApIdInMsg = v2xMessage.getRsuAPId(); //anota o RSU informado na mensagem
        String actualRsuApId;
        //getLog().infoSimTime(this,"Received_{}; sent_by_{};",v2xMessage.getMsgType(),rsuApIdInMsg);
        String vhId = v2xMessage.mappedV2xMsg.get("vhId");
        String serviceId = "service_01"; //Serviço que o veículo deseja consumir.

        VfnConnectedVehicle connectedVehicle;
        String rsuRunnerOfService;

        if(!globalDynamicMap.getVfnVehiclesMap().containsKey(vhId)){
            //Se o veículo não está mapeado, registra a primeira conexão do veículo. Definir rsuRunner e enviar regras OF para switches que compôem o path.
            this.globalDynamicMap.addVehicleInfo(v2xMessage.mappedV2xMsg);//Atualização ou inserção de Dados do veículo.

            connectedVehicle= this.globalDynamicMap.getVfnVehiclesMap().get(vhId);  //Acesso aos dados do veículo que agora está incluído
            actualRsuApId = rsuApIdInMsg;

            //quando o veículo é adicionado pela primeira vez, cria-se as suas listas de RSU. Dividem os RSUs de acordo com o HeadingDifference com relação ao veículo.
            connectedVehicle.getListOfRsuLists().add(0,new PriorityConnectedRsuList(srvConfig.maxHeadingDifferenceList1));
            connectedVehicle.getListOfRsuLists().add(1,new PriorityConnectedRsuList(srvConfig.maxHeadingDifferenceList2));
            connectedVehicle.getListOfRsuLists().add(2,new PriorityConnectedRsuList(180F));


            electRsuServiceRunnerToVehicle(connectedVehicle,serviceId); //Elege o RSU_Service_Runner para o Veículo baseado no seu serviço consumido
            rsuRunnerOfService = connectedVehicle.getRsuOfservice(serviceId); //recupera o
            connectedVehicle.setActualRsuRunnerPath(sdnController.getPathToRsu(rsuApIdInMsg,rsuRunnerOfService)); //SDN Controller calcula o path entre o RSU-AP e o RSU-SERVICE-RUNNER
            connectedVehicle.setLastRsuApId(connectedVehicle.getRsuApId()); //Neste caso de primeira conexão, o lastRSU-AP será o mesmo do RSU-AP atual
            connectedVehicle.setLastRsuRunnerPath(connectedVehicle.getActualRsuRunnerPath()); //Neste caso de primeira conexão, o lastPath será o mesmo do Path Atual (Reference)
            connectedVehicle.setNextRsuApId(connectedVehicle.getRsuApId()); //Neste caso de primeira Conexão, o NextRSUAPId, será o mesmo do atual
            connectedVehicle.setNextRsuRunnerPath(connectedVehicle.getActualRsuRunnerPath()); //Neste caso de primeira Conexão, o nextpath será o mesmo do path atual
            ArrayList<String> pathToRunnerArray = sdnController.getStrPathToRsu(rsuApIdInMsg,rsuRunnerOfService);
            sdnController.sendVehicleServiceRuleToRsuSwitch(pathToRunnerArray,vhId,serviceId,rsuRunnerOfService);// envio de regras baseados em vehicle/service origem e RSU destino.
            sdnController.sendRuleToRsuSwitch(pathToRunnerArray); //envio de regras para o RSU access point, baseada em RSU origem e destino
            getLog().infoSimTime(this,"SDN rules after FIRST vehicle connection. \n" +
                    "Vehicle: {} \n" +
                    "Predicted-AP: {} \n" +
                    "Actual-AP: {}",connectedVehicle.getVechicleId(),connectedVehicle.getNextRsuId(),rsuApIdInMsg);
        }else{
            //veículo já consta na base de dados
            connectedVehicle= this.globalDynamicMap.getVfnVehiclesMap().get(vhId);  //Acesso aos dados do veículo já incluído durante a primeira conexão
            actualRsuApId = connectedVehicle.getRsuApId(); //recupera o RSU-AP atual que está na base
            this.globalDynamicMap.addVehicleInfo(v2xMessage.mappedV2xMsg);//Atualização ou inserção de Dados do veículo. Inclui a atualização do RSU-AP da base para o que veio junto na mensagem
            rsuRunnerOfService = connectedVehicle.getRsuOfservice(serviceId); //recupera o rsuRunner que ja está definido na base
        }

        if(!Objects.equals(rsuApIdInMsg, actualRsuApId)){
            //RSU-AP informado diferente do atual, houve handover
            connectedVehicle.setLastRsuApId(actualRsuApId); //O RSU-AP que está na base passa ser o lastRSU-AP
            connectedVehicle.setLastRsuRunnerPath(connectedVehicle.getActualRsuRunnerPath()); //o path atual é armazenado como Last path


            if(!Objects.equals(rsuApIdInMsg,connectedVehicle.getNextRsuId())){
                //Predição de Next RSU-AP não se confirmou
                connectedVehicle.setActualRsuRunnerPath(sdnController.getPathToRsu(rsuApIdInMsg,rsuRunnerOfService)); //gera o path para o novo RSU-AP até o RSU-Runner
                ArrayList<String> pathToRunnerArray = sdnController.getStrPathToRsu(rsuApIdInMsg,rsuRunnerOfService);
                sdnController.sendVehicleServiceRuleToRsuSwitch(pathToRunnerArray,vhId,serviceId,rsuRunnerOfService);//envio de regras baseados em vehicle/service origem e RSU destino.
                sdnController.sendRuleToRsuSwitch(pathToRunnerArray);// Envio de regras para o RSU access point, após veículo já estar no próximo RSU-AP.
                getLog().infoSimTime(this,"SDN rules after UMPREDICTED vehicle connection. \n" +
                        "Vehicle: {} \n" +
                        "Predicted-AP: {} \n" +
                        "Actual-AP: {}",connectedVehicle.getVechicleId(),connectedVehicle.getNextRsuId(),rsuApIdInMsg);

            }else{
                //predição foi bem sucedida. O predicted path é eleito como o path atual
                connectedVehicle.setActualRsuRunnerPath(connectedVehicle.getNextRsuRunnerPath()); //predições confirmadas não precisam de cálculo de path. É o mesmo já previsto

            }

            //Se já houve a migração de RSUAP, deve-se remover regras do tipo veículo/serviço dos switches do path anterior.
            ArrayList<NetworkNode> differenceNodeList = sdnController.getPathDifference(connectedVehicle.getActualRsuRunnerPath(),connectedVehicle.getlastRsuRunnerPath());
            ArrayList<String> strDifferenceNodeList = new ArrayList<>();
            for (NetworkNode netNode: differenceNodeList) {
                strDifferenceNodeList.add(netNode.getRsuId());
            }
            sdnController.removeRsuSwitchServiceRule(strDifferenceNodeList,vhId,serviceId);

        }//Se não entrou no If(condicional) então não houve handover

        connectedVehicle.setDistanceVhToRsu(this.rsuPositionsMap.get(rsuApIdInMsg));//Atualiza a distância para o RSU_AP
        String nextPredictedRsuAP;
        String actualPredictedRsuAP;
        actualPredictedRsuAP = connectedVehicle.getNextRsuId();
        //nextPredictedRsuAP = rsuPredictor.predictNextRsuToVehicle(connectedVehicle); //tendo havido ou não o handover, o predicted Next RSU é recalculado
        nextPredictedRsuAP = nextRsuSelector.selectNextRsuToVehicle(connectedVehicle);
        /*if(v2xMessage.getMappedMsg().containsKey("nextRsuId")){
            nextPredictedRsuAP = v2xMessage.mappedV2xMsg.get("nextRsuId"); //se há mensagem informando o NextRSU, então usá-lo. Se não há, então
        }else{
            nextPredictedRsuAP = actualPredictedRsuAP;
        }*/
        //nextPredictedRsuAP = connectedVehicle.getRsuApId();//sem antecipação de rotas

        if(!Objects.equals(nextPredictedRsuAP,actualPredictedRsuAP)){ //Se o Predicted RSU mudou, deve-se fazer um novo caminho para a nova predição
            //houve uma mudança de predição de próxima RSU

            //Obs. Se Estava conectado no atual, não remover as regras do caminho atual

            //Agora deve-se atualizar o path para o novo predictedRSU e remover as regras que estão no differencePath.
            ArrayList<NetworkNode> newPredicteRsuApPath = sdnController.getPathToRsu(nextPredictedRsuAP,rsuRunnerOfService);

            if(!Objects.equals(actualPredictedRsuAP, connectedVehicle.getRsuApId())){ //compara antes da atualização
                //Somente remover regras do último predicted path se era diferente do atual path
                ArrayList<NetworkNode> differenceNodeList = sdnController.getPathDifference(newPredicteRsuApPath,connectedVehicle.getNextRsuRunnerPath());
                ArrayList<String> strDifferenceNodeList = new ArrayList<>();
                for (NetworkNode netNode: differenceNodeList) {
                    strDifferenceNodeList.add(netNode.getRsuId());
                }
                sdnController.removeRsuSwitchServiceRule(strDifferenceNodeList,vhId,serviceId); //Remove a regras dos switches que não estão mais no PredictedPath
            }

            connectedVehicle.setNextRsuApId(nextPredictedRsuAP); //atualiza o next RSU-AP nos dados do veículo
            connectedVehicle.setNextRsuRunnerPath(newPredicteRsuApPath);
            ArrayList<String> pathToRunnerArray = sdnController.getStrPathToRsu(nextPredictedRsuAP,rsuRunnerOfService);
            sdnController.sendVehicleServiceRuleToRsuSwitch(pathToRunnerArray,vhId,serviceId,rsuRunnerOfService);
            sdnController.sendRuleToRsuSwitch(pathToRunnerArray);//disparar o envio de regras para o predicted RSU  access point, baseada em RSU origem e destino
        }

        if(Objects.equals(v2xMessage.getMsgType(), this.srvConfig.vfnServiceMsgType)){
            //se mensagem era de Service, significa que foi enviada pela RSU por não saber qual é o RSU_Runner do Veículo
            //getLog().info("-------------------------------------------------");
            //Gera String da mensagem de resposta
            String coreMsg = "#vhId="+vhId+
                    ";msgType="+this.srvConfig.rsuRunnerMsgType+
                    ";netDestAddress="+this.srvConfig.rsuNet+
                    ";unitDestId="+rsuApIdInMsg+
                    ";serviceId="+serviceId+
                    ";rsuServiceRunner="+rsuRunnerOfService+
                    "#";
            //enviar mensagem de resposta ao RSU solicitante
            this.sendRsuServiceRunnerDecision(v2xMessage.mappedV2xMsg.get("rsuId"),coreMsg);
        }


        //LOGGING
        //getLog().infoSimTime(this,"Next_RSU-AP:"+connectedVehicle.getNextRsuId());
        //LogUtils.vehicleGDMLog(this,getLog(),this.globalDynamicMap.getVfnVehiclesMap(),"Dynamic_Vehicle_List");

    }


    /**
     * O método contem a lógica de escolha do RSU-runner para o veículo connectado
     * @param serviceId String que contem o serviço para o qual o veículo
     */
    private void electRsuServiceRunnerToVehicle(VfnConnectedVehicle connectedVehicle, String serviceId){

        String rsuOfService = connectedVehicle.getRsuOfservice(serviceId); //Identifica a RSU do Serviço solicitado
        if(Objects.equals(rsuOfService, "unused")){
            if(Objects.equals(srvConfig.fcnDistributionType, "roundRobin")){
                this.currentIndexFogComp = (this.currentIndexFogComp +1)%srvConfig.fcnList.size();
                connectedVehicle.setRsuServiceRunner(serviceId, srvConfig.fcnList.get(currentIndexFogComp));
            }

        }else{

            //Se já existia um RSU executando, executa a lógica de alteração de RSU
            //Criar lógica de alterações
        }
    }


    public void sendRsuServiceRunnerDecision (String rsuId, String coreMsg){

        final MessageRouting routing = getOs().getCellModule().createMessageRouting().protocol(ProtocolType.TCP).topoCast(rsuId);
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

    @Override
    public void onShutdown() {
        /*getLog().infoSimTime(this, "Mapeamento de RSUs");
        for (Map.Entry<String,MutableGeoPoint> entry: this.rsuPositionsMap.entrySet()) {
            getLog().info(sdnController.getNetworkTopology().getNetNode(entry.getKey()).toString());
        }*/

        getLog().infoSimTime(this, "Shutdown VFN Controller Server App");
    }

}
