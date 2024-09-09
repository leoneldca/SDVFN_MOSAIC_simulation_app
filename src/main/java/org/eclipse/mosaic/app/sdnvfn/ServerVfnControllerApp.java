package org.eclipse.mosaic.app.sdnvfn;

import org.eclipse.mosaic.app.sdnvfn.config.ServerConfig;
import org.eclipse.mosaic.app.sdnvfn.information.GlobalDynamicVehicleMap;
import org.eclipse.mosaic.app.sdnvfn.information.VfnConnectedVehicle;
import org.eclipse.mosaic.app.sdnvfn.message.ControllerServerMsg;
import org.eclipse.mosaic.app.sdnvfn.message.GenericV2xMessage;
import org.eclipse.mosaic.app.sdnvfn.network.CommunicationInterface;
import org.eclipse.mosaic.app.sdnvfn.network.NetworkNode;
import org.eclipse.mosaic.app.sdnvfn.network.RsuPredictor;
import org.eclipse.mosaic.app.sdnvfn.network.SdnController;
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

import java.util.*;

public class ServerVfnControllerApp extends ConfigurableApplication<ServerConfig, ServerOperatingSystem> implements CommunicationApplication {

    private GlobalDynamicVehicleMap globalDynamicMap;
    private CommunicationInterface communicationInterface;
    private ServerConfig srvConfig;
    private SdnController sdnController;
    private HashMap<String,MutableGeoPoint> rsuPositionsMap;
    private Map<String, NetworkNode> rsuNodesMap;
    private RsuPredictor rsuPredictor;
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
        rsuPredictor = new RsuPredictor(this.rsuPositionsMap); //instanciação do módulo RsuPredictor

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
            getLog().infoSimTime(this,"PacketIn:{}",v2xMessage.getCoreMsg());
            if(Objects.equals(v2xMessage.mappedV2xMsg.get("ofMsg"), srvConfig.openFlowPacketInMsg)){
                ArrayList<String> pathToRunnerArray = sdnController.getStrPathToRsu(v2xMessage.getRsuAPId(),v2xMessage.getUnitDestId());
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

            electRsuServiceRunnerToVehicle(connectedVehicle,serviceId); //Elege o RSU_Service_Runner para o Veículo baseado no seu serviço consumido
            rsuRunnerOfService = connectedVehicle.getRsuOfservice(serviceId); //recupera o
            connectedVehicle.setActualRsuRunnerPath(sdnController.getPathToRsu(rsuApIdInMsg,rsuRunnerOfService)); //SDN Controller calcula o path entre o RSU-AP e o RSU-SERVICE-RUNNER
            connectedVehicle.setLastRsuApId(connectedVehicle.getRsuApId()); //Neste caso de primeira conexão, o lastRSU-AP será o mesmo do RSU-AP atual
            connectedVehicle.setLastRsuRunnerPath(connectedVehicle.getActualRsuRunnerPath()); //Neste caso de primeira conexão, o lastPath será o mesmo do Path Atual
            connectedVehicle.setNextRsuApId(connectedVehicle.getRsuApId()); //Neste caso de primeira Conexão, o NextRSUAPId, será o mesmo do atual
            connectedVehicle.setNextRsuRunnerPath(connectedVehicle.getActualRsuRunnerPath()); //Neste caso de primeira Conexão, o nextpath será o mesmo do path atual
            ArrayList<String> pathToRunnerArray = sdnController.getStrPathToRsu(rsuApIdInMsg,rsuRunnerOfService);
            sdnController.sendVehicleServiceRuleToRsuSwitch(pathToRunnerArray,vhId,serviceId,rsuRunnerOfService);// envio de regras baseados em vehicle/service origem e RSU destino.
            sdnController.sendRuleToRsuSwitch(pathToRunnerArray); //envio de regras para o RSU access point, baseada em RSU origem e destino
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
            connectedVehicle.setLastRsuRunnerPath(connectedVehicle.getActualRsuRunnerPath()); //o path atual para a ser o Last path


            if(!Objects.equals(rsuApIdInMsg,connectedVehicle.getNextRsuId())){
                //Predição de Next RSU-AP não se confirmou
                connectedVehicle.setActualRsuRunnerPath(sdnController.getPathToRsu(rsuApIdInMsg,rsuRunnerOfService)); //gera o path para o novo RSU-AP até o RSU-Runner
                ArrayList<String> pathToRunnerArray = sdnController.getStrPathToRsu(rsuApIdInMsg,rsuRunnerOfService);
                sdnController.sendVehicleServiceRuleToRsuSwitch(pathToRunnerArray,vhId,serviceId,rsuRunnerOfService);//envio de regras baseados em vehicle/service origem e RSU destino.
                sdnController.sendRuleToRsuSwitch(pathToRunnerArray);// Envio de regras para o RSU access point, após veículo já estar no próximo RSU-AP.
                getLog().infoSimTime(this,"SDN rules after handover");

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

        }

        connectedVehicle.setDistanceVhToRsu(this.rsuPositionsMap.get(rsuApIdInMsg));//Atualiza a distância para o RSU_AP
        String nextRsuAP = rsuPredictor.predictNextRsuToVehicle(connectedVehicle); //tendo havido ou não o handover, o predicted Next RSU é recalculado


        if(!Objects.equals(nextRsuAP,connectedVehicle.getNextRsuId())){ //Se o Predicted RSU mudou, deve-se fazer um novo caminho para a nova predição
            //houve uma mudança de predição de próxima RSU
            connectedVehicle.setNextRsuApId(nextRsuAP); //atualiza o next RSU-AP nos dados do veículo
            //Agora deve-se atualizar o path para o novo predictedRSU e remover as regras que estão no differencePath.
            ArrayList<NetworkNode> newPredicteRsuApPath = sdnController.getPathToRsu(nextRsuAP,rsuRunnerOfService);
            ArrayList<NetworkNode> differenceNodeList = sdnController.getPathDifference(newPredicteRsuApPath,connectedVehicle.getNextRsuRunnerPath());
            ArrayList<String> strDifferenceNodeList = new ArrayList<>();
            for (NetworkNode netNode: differenceNodeList) {
                strDifferenceNodeList.add(netNode.getRsuId());
            }
            sdnController.removeRsuSwitchServiceRule(strDifferenceNodeList,vhId,serviceId); //Remove a regras dos switches que não estão mais no PredictedPath
            connectedVehicle.setNextRsuRunnerPath(newPredicteRsuApPath);
            ArrayList<String> pathToRunnerArray = sdnController.getStrPathToRsu(nextRsuAP,rsuRunnerOfService);
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
     * O método recebe mensagem de beacon de veículo.
     * Mensagem é enviada pelo RSU-AP do veículo
     * Utiliza os dados da mensagem e atualiza as informações do veículo na tabela GlobalDynamicMap
     * Pode ter havido Handover de RSU-AP (Fica registrado pelo métdodo de atualização de da GlobaDynamicMap)
     * Atualiza a distãncia entre o veículo e o RSU-AP vigente.
     * realiza a predição de próximo RSU-AP e atualiz esta informação nos dados do veículo.
     *
     * @param v2xMessage Neste método a mensagem em questão é um beacon de veículo
     */
    public void vehicleBeaconMsgHandle(GenericV2xMessage v2xMessage){

        String rsuAp = v2xMessage.getRouting().getSource().getSourceName();
        getLog().info("Received_VehicleBeaconMessage_sent_by:{}",rsuAp);
        String vhId = v2xMessage.mappedV2xMsg.get("vhId");

        this.globalDynamicMap.addVehicleInfo(v2xMessage.mappedV2xMsg);//Atualização ou inserção de Dados do veículo no mapeamento
        VfnConnectedVehicle connectedVehicle = this.globalDynamicMap.getVfnVehiclesMap().get(vhId);//Veículo cujos dados foram atualizados


        connectedVehicle.setDistanceVhToRsu(this.rsuPositionsMap.get(connectedVehicle.getRsuApId()));
        //Atualiza a distância para o RSU_AP de acordo com os dados vindos do veículo
        String predictedNextRsuAP = rsuPredictor.predictNextRsuToVehicle(connectedVehicle);
        String actualNextRsuAP = connectedVehicle.getNextRsuId();

        if(!Objects.equals(predictedNextRsuAP,actualNextRsuAP)){ //Se há uma pevisão que é diferente da atual, atualiza os dados com a nova previsão.
            connectedVehicle.setNextRsuApId(predictedNextRsuAP); //deve haver a atualização do path para o nextRSUAP.
            if(!Objects.equals(connectedVehicle.getNextRsuId(), connectedVehicle.getRsuApId())){
                ///Se há a PREVISÃO de migração para OUTRO RSU_AP, antecipa-se o envio de regras de encaminhamento para o RSU_RUNNER.
                ///Quando não há consumo de serviço, a própria função bloqueia o envio de regras.
                if(connectedVehicle.getServiceRsuMap().containsKey("service_01")){
                    String rsuRunnerOfService = connectedVehicle.getRsuOfservice("service_01");
                    connectedVehicle.setNextRsuRunnerPath(sdnController.getPathToRsu(connectedVehicle.getNextRsuId(),rsuRunnerOfService));
                }
                //sendRuleToPredictedRsu(connectedVehicle);
            }
        }

        if(!Objects.equals(connectedVehicle.getRsuApId(), connectedVehicle.getLastRsuApId())){
            //Se já houve a migração, deve-se remover do RSU_AP anterior a regra do tipo veículo/serviço
            ArrayList<String> outOfPathRsuList = new ArrayList<>(); //Lista de RSUs que estão fora do novo caminho.
            outOfPathRsuList.add(connectedVehicle.getLastRsuApId());
            //Neste caso apenas será removida a regra do RSU_AP anterior,
            // mas para que seja completo, deve-se realizar a diferença entre o caminho anterior e o novo caminho.
            // todos os RSUs que somente estão no caminho anterior e não estão no novo caminho devem ter
            // suas regras veículo/serviço removidas para o veículo em questão.
            sdnController.removeRsuSwitchServiceRule(outOfPathRsuList,vhId,"service_01");
        }
        //LOGGING
        getLog().infoSimTime(this,"Next_RSU-AP: "+connectedVehicle.getNextRsuId());
        LogUtils.vehicleGDMLog(this,getLog(),this.globalDynamicMap.getVfnVehiclesMap(),"Dynamic_Vehicle_List");

    }



    /**
     * Este método objetiva realizar a tomada de decisão sobre a escolha do RSU para a execução de Serviços para cada veículo.
     * @param v2xMessage mensagem VFNServiceMsg para ser tratada pelo método.
     */
    public void serviceMsgHandle(GenericV2xMessage v2xMessage){

        String rsuAp = v2xMessage.getRouting().getSource().getSourceName();
        getLog().info("Received_VFNServiceMsg_sent_by:{}",rsuAp);
        String vhId = v2xMessage.mappedV2xMsg.get("vhId");
        String serviceId = v2xMessage.mappedV2xMsg.get("serviceId"); //Serviço que o veículo deseja consumir.

        this.globalDynamicMap.addVehicleInfo(v2xMessage.mappedV2xMsg);//Atualização ou inserção de Dados do veículo

        VfnConnectedVehicle connectedVehicle = this.globalDynamicMap.getVfnVehiclesMap().get(vhId);  //Veículo cujos dados foram atualizados
        connectedVehicle.setDistanceVhToRsu(this.rsuPositionsMap.get(connectedVehicle.getRsuApId()));//Atualiza a distância para o RSU_AP



        //Neste caso é uma solicitação de RsuRunner
        this.electRsuServiceRunnerToVehicle(connectedVehicle,serviceId); // VFNService-Messages só chegam ao Servidor quando NÃO há RSU definido.
        String rsuRunnerOfService = connectedVehicle.getRsuOfservice(v2xMessage.mappedV2xMsg.get("serviceId"));
        //Identifica a RSU_Service_Runner, após sua escolha


        //getLog().info("-------------------------------------------------");
        //Gera String da mensagem de resposta
        String coreMsg = "#vhId="+vhId+
                ";msgType="+this.srvConfig.rsuRunnerMsgType+
                ";netDestAddress="+this.srvConfig.rsuNet+
                ";unitDestId="+rsuAp+
                ";serviceId="+serviceId+
                ";rsuServiceRunner="+rsuRunnerOfService+
                "#";


        connectedVehicle.setActualRsuRunnerPath(sdnController.getPathToRsu(rsuAp,rsuRunnerOfService));

        ArrayList<String> pathToRunnerArray = sdnController.getStrPathToRsu(rsuAp,rsuRunnerOfService);
        //disparar o envio de regras baseados em vehicle/service origem e RSU destino.
        sdnController.sendVehicleServiceRuleToRsuSwitch(pathToRunnerArray,vhId,serviceId,rsuRunnerOfService);

        //disparar o envio de regras para o RSU access point, baseada em RSU origem e destino
        sdnController.sendRuleToRsuSwitch(pathToRunnerArray);

        //enviar mensagem de resposta ao RSU solicitante
        this.sendRsuServiceRunnerDecision(v2xMessage.mappedV2xMsg.get("rsuId"),coreMsg);


        String nextRsuAP = rsuPredictor.predictNextRsuToVehicle(connectedVehicle); //predição do próximo RSU-AP

        if(!Objects.equals(nextRsuAP,connectedVehicle.getNextRsuId())){
            //O próximo será diferente
            connectedVehicle.setNextRsuApId(nextRsuAP);//armazena a predição
            if(!Objects.equals(connectedVehicle.getNextRsuId(), connectedVehicle.getRsuApId())){
                //Se nextRsuAP for diferente da RsuAP-Atual gera e nvia as regras de encaminhamento dos pacotes da nextRsuAP para
                connectedVehicle.setNextRsuRunnerPath(sdnController.getPathToRsu(connectedVehicle.getNextRsuId(),rsuRunnerOfService));
                //sendRuleToPredictedRsu(connectedVehicle);
            }
        }



        if(!Objects.equals(connectedVehicle.getRsuApId(), connectedVehicle.getLastRsuApId())){
            //Se já houve a migração de RSUAP, deve-se remover do RSU_AP anterior a regra do tipo veículo/serviço
            ArrayList<String> outOfPathRsuList = new ArrayList<>(); //Lista de RSUs que estão fora do novo caminho.
            outOfPathRsuList.add(connectedVehicle.getLastRsuApId());
            //Neste caso apenas será removida a regra do RSU_AP anterior,
            // mas para que seja completo, deve-se realizar a diferença entre o caminho anterior e o novo caminho.
            // todos os RSUs que somente estão no caminho anterior e não estão no novo caminho devem ter
            // suas regras veículo/serviço removidas para o veículo em questão.
            sdnController.removeRsuSwitchServiceRule(outOfPathRsuList,vhId,"service_01");
        }

        //LOGGING
        getLog().infoSimTime(this,"Next_RSU-AP: "+connectedVehicle.getNextRsuId());
        LogUtils.vehicleGDMLog(this,getLog(),this.globalDynamicMap.getVfnVehiclesMap(),"Dynamic_Vehicle_List");

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
