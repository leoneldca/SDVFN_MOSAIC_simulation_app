package org.eclipse.mosaic.app.sdnvfn.message;

import java.util.ArrayList;
import java.util.LinkedList;

public class LdmVehicleDataBuffer {

    private static LinkedList<String> messageBuffer = new LinkedList<>();

    public void addMsgToLdmBuffer(String msg){
        messageBuffer.addLast(msg);
    }

    public String getMsgToProcess(){

        if(!messageBuffer.isEmpty()){
            String msgToProcess = messageBuffer.getFirst();
            messageBuffer.removeFirst();
            return msgToProcess;
        }
        return "blank";
    }


}
