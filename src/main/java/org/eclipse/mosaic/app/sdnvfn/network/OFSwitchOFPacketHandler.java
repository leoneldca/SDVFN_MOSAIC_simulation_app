package org.eclipse.mosaic.app.sdnvfn.network;

import org.eclipse.mosaic.app.sdnvfn.config.RsuOFSwitchAppConfig;
import org.eclipse.mosaic.app.sdnvfn.message.GenericV2xMessage;
import org.eclipse.mosaic.fed.application.app.api.OperatingSystemAccess;
import org.eclipse.mosaic.fed.application.app.api.os.OperatingSystem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

public class OFSwitchOFPacketHandler {
    private FlowTable flowTable;
    private final OperatingSystemAccess<? extends OperatingSystem> unitOsAccess;
    private final OFSwitchPorts ofSwitchPorts;
    private final RsuOFSwitchAppConfig rsuOFSwitchConfig;
    private static final String ACTION_TYPE = "actionType";
    private static final String FORWARD = "FORWARD";


    public OFSwitchOFPacketHandler(FlowTable flowTable, OperatingSystemAccess<? extends OperatingSystem> unitOsAccess, OFSwitchPorts ofSwitchPorts, RsuOFSwitchAppConfig rsuOFSwitchConfig) {
        this.flowTable = flowTable;
        this.unitOsAccess = unitOsAccess;
        this.ofSwitchPorts = ofSwitchPorts;
        this.rsuOFSwitchConfig = rsuOFSwitchConfig;
    }

    /**
     * Método recebe uma mensagem do controlador pelo canal de controle e executa as ações solicitadas.
     * Mensagem de alteração de entradas na tabela.
     * @param v2xMessage Mensagem V2X Generica
     */
    public void ofPacketReceiver(GenericV2xMessage v2xMessage){

        if(!Objects.equals(v2xMessage.getMsgType(), this.rsuOFSwitchConfig.openFlowMsgType)){
            System.out.println("Tipo de mensagem inválida");
            return;
        }

        if(Objects.equals(v2xMessage.mappedV2xMsg.get("ofMsg"), this.rsuOFSwitchConfig.openFlowFlowMod)){
            String machingFields="";
            String actions="";
            int priority = Integer.parseInt(v2xMessage.mappedV2xMsg.get("priority"));
            machingFields = this.extractFieldsFromOfMsg(v2xMessage,"machingFields");

            if(Objects.equals(v2xMessage.mappedV2xMsg.get("ofModType"), "addFlowEntry")){
                actions = this.extractFieldsFromOfMsg(v2xMessage,"actions");
                flowTable.addFlowEntry(machingFields,actions,priority);
            }
            else if(Objects.equals(v2xMessage.mappedV2xMsg.get("ofModType"), "delFlowEntry")){
                //implementar função de remoção de entrada na flow table
                flowTable.removeFlowEntry(machingFields);
            }

        }
    }

    public  String[] getPathMap(String strPath){
        return strPath.split(",");
    }

    private String extractFieldsFromOfMsg(GenericV2xMessage v2xMessage, String fieldName){
        String strFields="";
        String[] fieldsArray = v2xMessage.mappedV2xMsg.get(fieldName).split("[,:]");
        for(int i=0; i<=fieldsArray.length-2;i=i+2){
            strFields = strFields.concat(fieldsArray[i]+"="+fieldsArray[i+1]);
            if(i<fieldsArray.length-2){
                strFields = strFields.concat(",");
            }
        }
        return strFields;
    }

}
