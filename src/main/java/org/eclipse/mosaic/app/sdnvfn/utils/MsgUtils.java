package org.eclipse.mosaic.app.sdnvfn.utils;

import org.eclipse.mosaic.lib.objects.v2x.V2xMessage;

import java.util.HashMap;
import java.util.Map;

public class MsgUtils {

    public static HashMap<String,String> extractMsgToHash(String strV2xMsg){
        String[] strSplit = strV2xMsg.split("#");
        String strStage1 = strSplit[1];
        String[] strStage2 = strStage1.split(";");
        //In stage2 is spected strings in the format key=value
        HashMap<String,String> mappedV2xMsg = new HashMap<>();
        for (String s : strStage2) {
            String[] strEntry = s.split("=");
            //System.out.println("Esta é a entrada" + Arrays.toString(strEntry));
            mappedV2xMsg.put(strEntry[0], strEntry[1]);
        }
        return mappedV2xMsg;
    }

    public static String extractCoreMsg(V2xMessage v2xMsg){
        String[] strSplit = v2xMsg.toString().split("#");
        return "#"+strSplit[1]+"#";
    }

    public static String getMsgFromStringMap(HashMap<String,String> mappedServiceMsg){

        StringBuilder coreMsg = new StringBuilder("#");
        for (Map.Entry<String, String> keyEntry : mappedServiceMsg.entrySet()) {
            coreMsg.append(keyEntry.getKey());
            coreMsg.append("=");
            coreMsg.append(keyEntry.getValue());
            coreMsg.append(";");
        }
        coreMsg.append("#");

        return coreMsg.toString().replace(";#","#");

    }





}
