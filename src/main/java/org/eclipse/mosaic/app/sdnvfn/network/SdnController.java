package org.eclipse.mosaic.app.sdnvfn.network;

import org.eclipse.mosaic.app.sdnvfn.config.ServerConfig;
import org.eclipse.mosaic.app.sdnvfn.message.GenericV2xMessage;
import org.eclipse.mosaic.app.sdnvfn.utils.MsgUtils;
import org.eclipse.mosaic.fed.application.ambassador.util.UnitLogger;
import org.eclipse.mosaic.fed.application.app.api.OperatingSystemAccess;
import org.eclipse.mosaic.fed.application.app.api.os.OperatingSystem;
import org.eclipse.mosaic.lib.enums.ProtocolType;
import org.eclipse.mosaic.lib.geo.MutableGeoPoint;
import org.eclipse.mosaic.lib.objects.v2x.MessageRouting;
import org.jgrapht.GraphPath;
import org.jgrapht.graph.DefaultWeightedEdge;

import java.util.*;

/**
 * A classe SdnController recebe como parâmetros a arquitetura básica da rede e monta uma topologia da rede de RSUs
 */
public class SdnController {

    private final OperatingSystemAccess<? extends OperatingSystem> unitOsAccess;
    private final UnitLogger logger;
    private ServerConfig srvConfig;
    private final CommunicationInterface communicationInterface;

    private final NetworkTopology networkTopology;
    public SdnController(ArrayList<String> rsusConnections, HashMap<String, MutableGeoPoint> rsuPositionsMap, OperatingSystemAccess<? extends OperatingSystem> unitOsAccess, UnitLogger logger, ServerConfig srvConfig){
        this.unitOsAccess = unitOsAccess;
        this.logger = logger;
        //createRsuNetTopology(rsusConnections);
        networkTopology = new NetworkTopology(rsusConnections,rsuPositionsMap);
        this.srvConfig = srvConfig;
        this.communicationInterface = new CommunicationInterface(unitOsAccess);
    }

    /**
     * Método retorna o menor caminho entre dois Vértices(Networknodes RSUs)
     * Entrada: RsuSourceId e RsuTargetId
     * Retorno: ArrayList de Strings contendo o path do Indice 0 ao último índice
     * */
    public ArrayList<String> getStrPathToRsu(String rsuSourceId, String rsuTargetId){
        ArrayList<String> rsuPathArray = new ArrayList<>();
        GraphPath<NetworkNode, DefaultWeightedEdge> packetPath = networkTopology.getPathRsuToRsu(networkTopology.getNetNode(rsuSourceId),networkTopology.getNetNode(rsuTargetId));
        List<NetworkNode> rsuPathList = packetPath.getVertexList();
        for (NetworkNode rsuNode: rsuPathList) {
            rsuPathArray.add(rsuNode.getRsuId());
        }
        return rsuPathArray;
    }

    public ArrayList<NetworkNode> getPathToRsu(String rsuSourceId, String rsuTargetId){
        GraphPath<NetworkNode, DefaultWeightedEdge> packetPath = networkTopology.getPathRsuToRsu(networkTopology.getNetNode(rsuSourceId),networkTopology.getNetNode(rsuTargetId));
        return new ArrayList<>(packetPath.getVertexList());

    }

    public NetworkTopology getNetworkTopology() {
        return networkTopology;
    }


    public void sendPacketOut(GenericV2xMessage packetOut){
        packetOut.mappedV2xMsg.replace("msgType",this.srvConfig.vfnServiceMsgType);
        packetOut.mappedV2xMsg.remove("origMsgType");
        packetOut.mappedV2xMsg.remove("ofMsg");
        final MessageRouting routing = unitOsAccess.getOs().getCellModule().createMessageRouting().protocol(ProtocolType.TCP).
                topoCast(packetOut.mappedV2xMsg.get("rsuId"));
        unitOsAccess.getOs().getCellModule().sendV2xMessage(new GenericV2xMessage(routing, MsgUtils.getMsgFromStringMap(packetOut.mappedV2xMsg)));
    }


    /**
     * O método envia as regras OpenFlow do tipo vehiculo/serviço para todos os RSUs que compõe o caminho entre o
     * RSU-AP e o RSUServiceRunner.
     * @param rsuPathArray
     * @param vhId
     * @param serviceId
     * @param rsuServiceRunner
     */
    public void sendVehicleServiceRuleToRsuSwitch(ArrayList<String> rsuPathArray,String vhId, String serviceId, String rsuServiceRunner){
        String rsuCommPort="4";
        HashMap<String,String> mappedOFFlowModMsg = new HashMap<>();
        mappedOFFlowModMsg.put("msgType",this.srvConfig.openFlowMsgType);
        mappedOFFlowModMsg.put("ofMsg",srvConfig.openFlowFlowMod);
        mappedOFFlowModMsg.put("ofModType","addFlowEntry");
        mappedOFFlowModMsg.put("machingFields","");
        mappedOFFlowModMsg.put("actions","");
        mappedOFFlowModMsg.put("priority","25");
        MessageRouting rsuMsgRoute;
        String strMatchingFields, strActions;
        boolean insertRule;
        NetworkNode rsuNetNode;
        if(rsuPathArray.size()>1){
            for(int i=0; i<rsuPathArray.size()-1;i++){
                insertRule = true;
                strMatchingFields = "msgType:"+srvConfig.vfnServiceMsgType+",vhId:"+vhId+",serviceId:"+serviceId;
                strActions = "actionType:FORWARD,port:"+rsuCommPort+",unitDestId:"+rsuPathArray.get(i+1)+",rsuServiceRunner:"+rsuServiceRunner;
                rsuNetNode = networkTopology.getNetNode(rsuPathArray.get(i));
                if(rsuNetNode.getFlowTableMap().containsKey(strMatchingFields)){
                    if(Objects.equals(rsuNetNode.getFlowTableMap().get(strMatchingFields), strActions)){
                       insertRule = false; //Nada a fazer, matchingField e Actions inalteradas.
                    }
                }
                if(insertRule){
                    //Se for a primeira inserção ou uma sobreposição, adicionar a regra
                    mappedOFFlowModMsg.replace("machingFields",strMatchingFields);
                    mappedOFFlowModMsg.replace("actions",strActions);
                    rsuNetNode.addFlowEntry(strMatchingFields,strActions); //adiciona regra no grafo da topologia
                    rsuMsgRoute = unitOsAccess.getOs().getCellModule().createMessageRouting().topoCast(rsuPathArray.get(i));
                    communicationInterface.sendCellV2xMessage(new GenericV2xMessage(rsuMsgRoute,mappedOFFlowModMsg));  //enviar para o RSU na posição i
                    logger.infoSimTime(unitOsAccess,"Send_OF_MOD_addFlowEntry_To:{}, matching_fields:{}, actions:{}",
                            rsuPathArray.get(i),
                            mappedOFFlowModMsg.get("machingFields"),
                            mappedOFFlowModMsg.get("actions"));
                }
            }
        }
    }


    /**
     * O Método recebe uma packetIn do RSU e envia FLOW_MOD para a adição de regras de encaminhamento
     * @param rsuPathArray representa uma lista de string com os nomes dos RSU que compõe o caminho entre o RSU-AP e o RSU Service Runner
     */
    public void sendRuleToRsuSwitch(ArrayList<String> rsuPathArray){
        HashMap<String,String> mappedOFFlowModMsg = new HashMap<>();
        String rsuCommPort="4";
        mappedOFFlowModMsg.put("msgType",this.srvConfig.openFlowMsgType);
        mappedOFFlowModMsg.put("ofMsg",srvConfig.openFlowFlowMod);
        mappedOFFlowModMsg.put("ofModType","addFlowEntry");
        mappedOFFlowModMsg.put("machingFields","");
        mappedOFFlowModMsg.put("actions","");
        mappedOFFlowModMsg.put("priority","100");
        MessageRouting rsuMsgRoute;
        String strMatchingFields, strActions;
        boolean insertRule;
        NetworkNode rsuNetNode;
        if(rsuPathArray.size()>2){
            for(int i=0; i<rsuPathArray.size();i++){
                for(int j=rsuPathArray.size()-1;j>i+1;j--){//Cria regras de 01 nó pra todos os demais no RSU-Path
                    insertRule = true;
                    strMatchingFields = "txType:CELL_TOPOCAST,unitDestId:"+rsuPathArray.get(j);
                    strActions = "actionType:FORWARD,port:"+rsuCommPort+",unitDestId:"+rsuPathArray.get(i+1);
                    rsuNetNode = networkTopology.getNetNode(rsuPathArray.get(i));
                    if(rsuNetNode.getFlowTableMap().containsKey(strMatchingFields)){
                        if(Objects.equals(rsuNetNode.getFlowTableMap().get(strMatchingFields), strActions)){
                            insertRule = false; //Nada a fazer, matchingField e Actions inalteradas.
                        }
                    }
                    if(insertRule){
                        //Se for a primeira inserção ou uma sobreposição, adicionar a regra
                        mappedOFFlowModMsg.replace("machingFields",strMatchingFields);
                        mappedOFFlowModMsg.replace("actions",strActions);
                        rsuNetNode.addFlowEntry(strMatchingFields,strActions); //adiciona regra no grafo da topologia
                        rsuMsgRoute = unitOsAccess.getOs().getCellModule().createMessageRouting().topoCast(rsuPathArray.get(i));
                        communicationInterface.sendCellV2xMessage(new GenericV2xMessage(rsuMsgRoute,mappedOFFlowModMsg));  //enviar para o RSU na posição i
                        logger.infoSimTime(unitOsAccess,"Send_OF_MOD_To:{}:{}",rsuPathArray.get(i),mappedOFFlowModMsg.toString());
                    }
                }
            }
        }

        Collections.reverse(rsuPathArray);
        if(rsuPathArray.size()>2){
            for(int i=0; i<rsuPathArray.size();i++){
                for(int j=rsuPathArray.size()-1;j>i+1;j--){//Cria regras de 01 nó pra todos os demais no RSU-Path
                    insertRule = true;
                    strMatchingFields = "txType:CELL_TOPOCAST,unitDestId:"+rsuPathArray.get(j);
                    strActions = "actionType:FORWARD,port:"+rsuCommPort+",unitDestId:"+rsuPathArray.get(i+1);
                    rsuNetNode = networkTopology.getNetNode(rsuPathArray.get(i));
                    if(rsuNetNode.getFlowTableMap().containsKey(strMatchingFields)){
                        if(Objects.equals(rsuNetNode.getFlowTableMap().get(strMatchingFields), strActions)){
                            insertRule = false; //Nada a fazer, matchingField e Actions inalteradas.
                        }
                    }
                    if(insertRule){
                        //Se for a primeira inserção ou uma sobreposição, adicionar a regra
                        mappedOFFlowModMsg.replace("machingFields",strMatchingFields);
                        mappedOFFlowModMsg.replace("actions",strActions);
                        rsuNetNode.addFlowEntry(strMatchingFields,strActions); //adiciona regra no grafo da topologia
                        rsuMsgRoute = unitOsAccess.getOs().getCellModule().createMessageRouting().topoCast(rsuPathArray.get(i));
                        communicationInterface.sendCellV2xMessage(new GenericV2xMessage(rsuMsgRoute,mappedOFFlowModMsg));  //enviar para o RSU na posição i
                        logger.infoSimTime(unitOsAccess,"Send_OF_MOD_To:{}:{}",rsuPathArray.get(i),mappedOFFlowModMsg.toString());
                    }


                }
            }
        }
    }

    public void removeRsuSwitchServiceRule(ArrayList<String> rsuPathArray, String vhId, String serviceId){
        HashMap<String,String> mappedOFFlowModMsg = new HashMap<>();
        mappedOFFlowModMsg.put("msgType",this.srvConfig.openFlowMsgType);
        mappedOFFlowModMsg.put("ofMsg",srvConfig.openFlowFlowMod);
        mappedOFFlowModMsg.put("ofModType","delFlowEntry");
        mappedOFFlowModMsg.put("machingFields","");
        mappedOFFlowModMsg.put("priority","25");
        MessageRouting rsuMsgRoute;
        String strMatchingFields;
        NetworkNode rsuNetNode;
        for(int i=0; i<rsuPathArray.size();i++){
            strMatchingFields = "msgType:"+srvConfig.vfnServiceMsgType+",vhId:"+vhId+",serviceId:"+serviceId;
            mappedOFFlowModMsg.replace("machingFields",strMatchingFields);
            rsuNetNode = networkTopology.getNetNode(rsuPathArray.get(i));
            rsuNetNode.getFlowTableMap().remove(strMatchingFields); //remove regra no grafo da topologia

            ///remove a regra no Switch do RSU
            rsuMsgRoute = unitOsAccess.getOs().getCellModule().createMessageRouting().topoCast(rsuPathArray.get(i));
            communicationInterface.sendCellV2xMessage(new GenericV2xMessage(rsuMsgRoute,mappedOFFlowModMsg));  //enviar para o RSU na posição i
            logger.infoSimTime(unitOsAccess,"Send_OF_MOD_delFlowEntry_To:{}:{}",rsuPathArray.get(i),mappedOFFlowModMsg.toString());
        }
    }

    /**
     *
     * @param actualPath  Caminho 1 para o RSURunner
     * @param unusedPath Caminho 2 para o RSURunner
     * @return retorna a diferença entre eles
     * @param <T> O tipo de dados de retorno será uma lista do mesmo tipo que será passado para a função
     */

    public <T> ArrayList<T> getPathDifference(ArrayList<T> actualPath, ArrayList<T> unusedPath) {
        ArrayList<T> difference = new ArrayList<>(unusedPath); //cria um ArrayList difference que contenha os mesmos dados do pathAtual.
        difference.removeAll(actualPath);
        return difference;
    }




}
