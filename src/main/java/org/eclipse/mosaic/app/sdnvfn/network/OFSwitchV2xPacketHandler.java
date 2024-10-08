package org.eclipse.mosaic.app.sdnvfn.network;

import org.eclipse.mosaic.app.sdnvfn.config.RsuOFSwitchAppConfig;
import org.eclipse.mosaic.app.sdnvfn.message.GenericV2xMessage;
import org.eclipse.mosaic.app.sdnvfn.message.VfnServiceResultMsg;
import org.eclipse.mosaic.fed.application.app.api.OperatingSystemAccess;
import org.eclipse.mosaic.fed.application.app.api.os.OperatingSystem;
import org.eclipse.mosaic.lib.enums.AdHocChannel;
import org.eclipse.mosaic.lib.objects.v2x.MessageRouting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class OFSwitchV2xPacketHandler {
    private FlowTable flowTable;
    private final OperatingSystemAccess<? extends OperatingSystem> unitOsAccess;
    private final OFSwitchPorts ofSwitchPorts;
    private final RsuOFSwitchAppConfig rsuOFSwitchConfig;
    private static final String ACTION_TYPE = "actionType";
    private static final String FORWARD = "FORWARD";

    public OFSwitchV2xPacketHandler(FlowTable flowTable, OperatingSystemAccess<? extends OperatingSystem> unitOsAccess, OFSwitchPorts ofSwitchPorts, RsuOFSwitchAppConfig rsuOFSwitchConfig) {
        this.flowTable = flowTable;
        this.unitOsAccess = unitOsAccess;
        this.ofSwitchPorts = ofSwitchPorts;
        this.rsuOFSwitchConfig = rsuOFSwitchConfig;
    }

    public void packetMachingFunction(GenericV2xMessage v2xMessage){
        //percorrer a tabela de fluxos até que haja um match ou que se chegue no final
        //caso chegue ao final, então ocorreu uma table-miss.

        HashMap<String,String> mappedEntryMachingFields = new HashMap<>();
        HashMap<String,String> mappedEntryActions = new HashMap<>();
        HashMap<String,String> mappedMsgMatchingFields = this.getMsgMatchingFields(v2xMessage); //carrega as matching fields da mensagem

        //percorrendo tabela de fluxos (FlowTable)
        boolean match = false;
        for(ArrayList<String> flowEntry: this.flowTable.getFlowTable()){
            //para cada flowEntry mapear os matchingFields e actions deste FlowEntry
            this.mapEntryMachingFields(mappedEntryMachingFields,flowEntry.get(0).split(","));
            this.mapEntryActions(mappedEntryActions,flowEntry.get(1).split(","));
            match = this.isAMach(mappedEntryMachingFields,mappedMsgMatchingFields);
            if(match){
                //executar as actions relacionadas às matching fields que fizeram o match com a mensagem
                this.entryActionsRunner(mappedEntryMachingFields,mappedEntryActions,v2xMessage);
                break;
            }
        }
        if(!match){
            //Executar Table-Miss
            sendPacketInToController(v2xMessage);
        }


    }

    /**
     * Método que recebe uma string com as matching fields de uma entrada da FlowTable adiciona e mapea em uma Hash contendo
     * chave-valor
     *
     * @param mappedMatchingFields
     * @param strMatchingFields
     */
    private void mapEntryMachingFields(HashMap<String,String> mappedMatchingFields, String[] strMatchingFields){
        mappedMatchingFields.clear();
        for (String s : strMatchingFields) {
            String[] strEntry = s.split("=");
            mappedMatchingFields.put(strEntry[0], strEntry[1]);
        }
    }

    /**
     * O método mapeia chave-valor das actions de uma Entrada da FlowTable
     * @param mappedEntryActions
     * @param strEntryActions
     */
    private void mapEntryActions(HashMap<String,String> mappedEntryActions, String[] strEntryActions){
        mappedEntryActions.clear();
        for (String s : strEntryActions) {
            String[] strEntry = s.split("=");
            mappedEntryActions.put(strEntry[0], strEntry[1]);
        }
    }

    /**
     * O método recebe as fields de entrada da Flow table e as Fields da mensagem e verifica há correspondencia entre ambas
     * Caso todas as fields da entrada sejam satisfeitas, o método retornará o resultado booleano true, caso contrário retornará o booleano false
     * @param mappedEntryMachingFields
     * @param mappedMsgMatchingFields
     * @return
     */
    private Boolean isAMach(HashMap<String,String> mappedEntryMachingFields, HashMap<String,String> mappedMsgMatchingFields ){
        boolean match;
        match = true; //match receberá false caso alguma matchField não esteja na mensagem
        String msgFieldValue;
        String entryFieldValue;
        for(Map.Entry<String, String> keyEntry : mappedEntryMachingFields.entrySet()){
            entryFieldValue = keyEntry.getValue();
            msgFieldValue = mappedMsgMatchingFields.get(keyEntry.getKey());
            if(!Objects.equals(entryFieldValue, msgFieldValue)){
                match=false;
                break;
            }
        }
        return match;
    }


    /**
     * O método sendPacketIn é responsável por enviar um packetIn para o SDNControler
     * Recebe como parâmetro a mensagem que foi rejeitada. Encapsula em uma mensagem de Packet-In e envia para o SDN-Controller
     * @param v2xMessage
     */
    public void sendPacketInToController(GenericV2xMessage v2xMessage){
        MessageRouting messageRouting = unitOsAccess.getOs().getCellModule().createMessageRouting().
                topoCast(rsuOFSwitchConfig.serverName);
        v2xMessage.mappedV2xMsg.put("origMsgType",v2xMessage.mappedV2xMsg.get("msgType"));
        v2xMessage.mappedV2xMsg.replace("msgType",this.rsuOFSwitchConfig.openFlowMsgType);
        v2xMessage.mappedV2xMsg.put("ofMsg","packetIn");
        GenericV2xMessage packetIn = new GenericV2xMessage(messageRouting,v2xMessage.mappedV2xMsg);
        Integer port = ofSwitchPorts.getServerPort();

        ofSwitchPorts.sendMessage(port,packetIn); //Envia a mensagem já com as alterações
    }

    /**
     * O método recebe o conjunto de actions a serem aplicadas à mensagem.
     * A função retorna uma nova mensagem de encaminhamento já com as actions aplicadas.
     * Cada action pode gerar alteração diversas. Caso nenhuma alteração for realizada,
     * @param mappedEntryActions
     * @param originalMsg
     * @return
     */
    private GenericV2xMessage alterMsgToForward(HashMap<String,String> mappedEntryMachingFields, HashMap<String,String> mappedEntryActions, GenericV2xMessage originalMsg){
        //se há a action txType nas ações é porque há a necessidade de alteração para a que foi determinada a mensagem
        //Alterações no corpo da mensagem
        boolean msgChanged = false;
        MessageRouting messageRouting = originalMsg.getRouting(); //inicia apontando para a mensagem atual
        String newUnitDestId;
        String newDestination = new String();

        if(Objects.equals(mappedEntryActions.get("port"),"1") || Objects.equals(mappedEntryActions.get("port"),"3")){
            return originalMsg;
            //Porta 1 para mensagens direcionadas para o RSU ligado ao switch (Intraunit)
            //Porta 3 para mensagens vindas do RSU local para o servidor - canal openFlow (Porta 3 do servidor)
        }

        if(mappedEntryActions.containsKey("netDestAddress")){ //se a action altera destinatário
            originalMsg.mappedV2xMsg.replace("netDestAddress",mappedEntryActions.get("netDestAddress"));
            msgChanged = true;
        }

        if(mappedEntryActions.containsKey("rsuServiceRunner")){
            originalMsg.mappedV2xMsg.replace("rsuServiceRunner",mappedEntryActions.get("rsuServiceRunner"));
            msgChanged = true;
        }


        if(Objects.equals(mappedEntryActions.get("port"),"2")){
            //Porta 1: Mensagem destinada ao RSU de ligação direta.
            if(mappedEntryActions.containsKey("unitDestId")){
                //alterar destino da mensagem
                newUnitDestId = mappedEntryActions.get("unitDestId");
                if(Objects.equals(newUnitDestId, "vhId")){ //troca o destino para um veículo
                    originalMsg.mappedV2xMsg.replace("unitDestId",originalMsg.mappedV2xMsg.get(newUnitDestId)); //vhId
                }
                msgChanged = true;
                messageRouting = unitOsAccess.getOs().getAdHocModule().createMessageRouting().viaChannel(AdHocChannel.CCH).
                        topoCast(originalMsg.mappedV2xMsg.get("unitDestId"),rsuOFSwitchConfig.defautMsgHops);
                return new GenericV2xMessage(messageRouting,originalMsg.mappedV2xMsg); //Mensagem que chega de RSU remoto para o veículo
            }
            return originalMsg; //Mensagem enviada do RSU Local para o Veículo

        }

       if (Objects.equals(mappedEntryActions.get("port"),"4")) {
            if(mappedEntryActions.containsKey("unitDestId")){ //encaminhamento para RSUs a mais de 1 salto]
                messageRouting = unitOsAccess.getOs().getCellModule().createMessageRouting().
                        topoCast(mappedEntryActions.get("unitDestId"));
                if(Objects.equals(originalMsg.getMsgType(), rsuOFSwitchConfig.vfnServiceMsgType)){
                    originalMsg.mappedV2xMsg.replace("unitDestId", originalMsg.mappedV2xMsg.get("rsuServiceRunner"));
                }
                return new GenericV2xMessage(messageRouting,originalMsg.mappedV2xMsg);
            }
           messageRouting = unitOsAccess.getOs().getCellModule().createMessageRouting().
                   topoCast(originalMsg.mappedV2xMsg.get("unitDestId"));
            return new GenericV2xMessage(messageRouting,originalMsg.mappedV2xMsg);

        }

        return originalMsg;


    }

    /**
     * Este método aplica as ações determinandas na actions da "flow-entry" que fez "match".
     * @param mappedEntryActions
     * @param v2xMessage
     */
    private void entryActionsRunner(HashMap<String,String> mappedEntryMachingFields, HashMap<String,String> mappedEntryActions, GenericV2xMessage v2xMessage){
        //Percorrer cada ação definida na actions Entry executando-as
        if (Objects.equals(mappedEntryActions.get(ACTION_TYPE), FORWARD)){
            //sabe-se que a mensagem será reencaminhada.
            //verificar se haverá a mudança de destinatário ou será encapsulada.

            Integer port = Integer.valueOf(mappedEntryActions.get("port"));

            ofSwitchPorts.sendMessage(port,this.alterMsgToForward(mappedEntryMachingFields, mappedEntryActions,v2xMessage)); //Envia a mensagem já com as alterações
        }else
        if(Objects.equals(mappedEntryActions.get(ACTION_TYPE), "DROP")){
            //descartar mensagem
            System.out.println("Mensagem descartada");

        }


    }

    /**
     * O metodo recebe uma mensagem V2X e gera um mapeamento das possíveis matchingFields da mensagem
     *
     * @param v2xMessage
     * @return
     */
    private HashMap<String,String> getMsgMatchingFields(GenericV2xMessage v2xMessage){
        //Laço de inserção das maching fields presentes no corpo da mensagem
        //Campos comuns a todas as mensagens: netDestAddress, msgType
        HashMap<String, String> mappedMsgMatchingFields = new HashMap<>(v2xMessage.mappedV2xMsg);
        //maching field source Identification
        mappedMsgMatchingFields.put("unitSrcId",v2xMessage.getRouting().getSource().getSourceName());
        //maching field Destination Identification
        //mappedMsgMatchingFields.put("unitDestId",v2xMessage.getRouting().getDestination().getAddress().getIPv4Address().getHostName());
        //matching field Unit Destination Address
        mappedMsgMatchingFields.put("unitDestAddress",v2xMessage.getRouting().getDestination().getAddress().getIPv4Address().getHostAddress());
        //matching field Unit
        mappedMsgMatchingFields.put("txType",v2xMessage.getRouting().getDestination().getType().name());


        return mappedMsgMatchingFields;
    }

}


