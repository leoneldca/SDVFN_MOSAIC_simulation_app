package org.eclipse.mosaic.app.sdnvfn.network;

import org.eclipse.mosaic.app.sdnvfn.config.RsuOFSwitchAppConfig;
import org.eclipse.mosaic.fed.application.app.api.OperatingSystemAccess;
import org.eclipse.mosaic.fed.application.app.api.os.OperatingSystem;

import java.util.*;

/**
 * O switchOpenFlow recebe mensagens V2X para serem processadas.
 * - Necessário uma fila de processamento
 * O SwitchOpenFlow envia mensagens para  
 */
public class FlowTable {

    private final OperatingSystemAccess<? extends OperatingSystem> unitOsAccess;
    private final OFSwitchPorts ofSwitchPorts;
    private final RsuOFSwitchAppConfig rsuOFSwitchConfig;
    private LinkedList<ArrayList<String>> flowTable;
    private static final String ACTION_TYPE = "actionType";
    private static final String FORWARD = "FORWARD";

    public FlowTable(OperatingSystemAccess<? extends OperatingSystem> unitOsAccess, OFSwitchPorts ofSwitchPorts, RsuOFSwitchAppConfig rsuOFSwitchConfig){
        this.unitOsAccess = unitOsAccess;
        this.ofSwitchPorts = ofSwitchPorts;
        this.rsuOFSwitchConfig = rsuOFSwitchConfig;
        //criar a lógica básica do OpenFlow. O que fazer no momento da criação do objeto?
        //criar tabela de fluxos OpenFlow
        this.alocateFlowTable();
        //add initial flow entries
        this.addInitialFlowEntries();

    }
    public LinkedList<ArrayList<String>> getFlowTable(){
        return this.flowTable;
    }
    private void alocateFlowTable(){
        this.flowTable = new LinkedList<>();
    }



    private void addInitialFlowEntries(){
        String machingFields;
        String actions;

        //Server Network FlowEntry
        //Mensagens que chegam da RSU Local ou que vem da rede Ad-Hoc, e cujo o destino seja o Controlador/Orquestrador
        machingFields = "unitDestAddress="+rsuOFSwitchConfig.serverAddress;
        actions = ACTION_TYPE +"="+ FORWARD +",port=3";
        this.addFlowEntry(machingFields,actions,10);

        //Mensagens de resultado que chegam de RSUs. Estas devem ser encaminnhadas para o veículo destino final
        machingFields = "txType=CELL_TOPOCAST,msgType="+this.rsuOFSwitchConfig.serviceResultMsgType+",unitDestId="+unitOsAccess.getOs().getId();
        actions = ACTION_TYPE +"="+ FORWARD +",port=2,unitDestId=vhId,netDestAddress="+this.rsuOFSwitchConfig.vehicleNet+",txType=AD_HOC_TOPOCAST";
        this.addFlowEntry(machingFields,actions,20);

        //Outros tipos de mensagens que chegam com destino ao RSU Local - Exemplos CAMs, Beacons de Veículos, Mensagens de Controle do Servidor
        machingFields = "unitDestId="+unitOsAccess.getOs().getId();
        actions = ACTION_TYPE +"="+ FORWARD +",port=1";
        this.addFlowEntry(machingFields,actions,30);


        //Beacons de RSU e resultados de processamento feitos na RSU local.
        machingFields = "txType=AD_HOC_TOPOCAST,netDestAddress="+rsuOFSwitchConfig.vehicleNet;
        actions = ACTION_TYPE +"="+ FORWARD +",port=2";
        this.addFlowEntry(machingFields,actions,40);

        //encaminhamentos de mensagens com destino a RSUs vizinhos
        String[] pairRsuConn;
        String[] connectedRSUs;
        for (String rsuConnections: rsuOFSwitchConfig.rsusConnections) {
            pairRsuConn = rsuConnections.split(":");
            if(Objects.equals(pairRsuConn[0], unitOsAccess.getOs().getId())){
                connectedRSUs = pairRsuConn[1].split(",");
                for (int i=0;i<connectedRSUs.length;i++) {
                    //Mensagens com destino às RSUs, com ligação direta com a RSU local
                    machingFields = "unitDestId="+connectedRSUs[i].toString();
                    actions = ACTION_TYPE +"="+ FORWARD +",port=4";
                    this.addFlowEntry(machingFields,actions,i+50);
                }
                break;
            }

        }
    }

    public void removeFlowEntry(String matchingFields){
        for(int i=0;i<flowTable.size(); i++ ){
            if(Objects.equals(flowTable.get(i).get(0), matchingFields)){
                //ao encontrar uma regra igual, remove-la.
                this.flowTable.remove(i);
                break;
            }
        }
    }

    /**
     * O metodo adiciona uma nova entrada na tabela de fluxos. A prioridade de uma regra representa a sua posição na tabela.
     * Prioridade iniciam-se em 0 como mais prioritário e vão diminuindo conforme avança-se nas posições.
     *
     * @param matchingFields
     * @param actions
     * @param priority
     */
    public void addFlowEntry(String matchingFields, String actions,int priority){
        final int MF_INDEX=0;
        final int AF_INDEX=1;
        final int PRIORITY_INDEX=2;
        ArrayList<String> newFlowEntry = new ArrayList<>();
        newFlowEntry.add(MF_INDEX,matchingFields);
        newFlowEntry.add(AF_INDEX,actions);
        newFlowEntry.add(PRIORITY_INDEX,String.valueOf(priority));
        boolean entrySubstituted = false;
        if(flowTable.isEmpty()) {
            //adição de primeira entrada na tabela
            this.flowTable.add(0, newFlowEntry);
        }else {
            removeFlowEntry(newFlowEntry.get(0)); //Se existir uma flow entry igual, a mesma será removida
            boolean entryAdded = false;
            for(int i=0;i<flowTable.size(); i++ ){
                if(Integer.parseInt(this.flowTable.get(i).get(PRIORITY_INDEX))>Integer.parseInt(newFlowEntry.get(PRIORITY_INDEX))){
                    //adiciona a regra de acordo com a prioridade
                    this.flowTable.add(i,newFlowEntry);
                    entryAdded = true;
                    break;
                }
            }
            if(!entryAdded){
                this.flowTable.addLast(newFlowEntry);
            }
        }
    }
    @Override
    public String toString(){
        Iterator<ArrayList<String>> it = flowTable.iterator();
        if (!it.hasNext()) {
            return "[]";
        } else {
            StringBuilder sb = new StringBuilder();
            sb.append("\n[");

            while(true) {
                ArrayList<String> e = it.next();
                sb.append(e);
                if (!it.hasNext()) {
                    return sb.append(']').toString();
                }

                sb.append(',').append('\n');
            }
        }
    }

}
