package org.eclipse.mosaic.app.sdnvfn.message;

import org.eclipse.mosaic.lib.objects.v2x.EncodedPayload;
import org.eclipse.mosaic.lib.objects.v2x.MessageRouting;
import org.eclipse.mosaic.lib.objects.v2x.V2xMessage;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Map;


public class GenericV2xMessage extends V2xMessage {


    private final String msgStrData;
    private final EncodedPayload payload;
    private final static long minLen = 128L;
    public final HashMap<String,String> mappedV2xMsg = new HashMap<>();

    public GenericV2xMessage(MessageRouting routing,String msgStrData) {
        super(routing);
        payload = new EncodedPayload(16L, minLen);
        this.msgStrData = msgStrData;
        generateMappedMsg();
    }

    public GenericV2xMessage(MessageRouting routing, HashMap<String, String> mappedV2xMsg){
        super(routing);
        payload = new EncodedPayload(16L, minLen);
        this.msgStrData = this.getMsgFromStrHashMap(mappedV2xMsg);
        generateMappedMsg();
    }

    @Nonnull
    @Override
    public EncodedPayload getPayLoad() {
        return payload;
    }

    /* (non-Javadoc)
     * @see org.eclipse.mosaic.lib.objects.v2x.V2xMessage#toString()
     */
    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer("V2xMessage{");
        sb.append(msgStrData);
        sb.append('}');
        return sb.toString();
    }
    public String getMsgType(){
        return this.mappedV2xMsg.get("msgType");
    }

    public String getUnitDestId(){
        return this.mappedV2xMsg.get("unitDestId");
    }

    public String getRsuAPId(){
        return this.mappedV2xMsg.get("rsuId");
    }


    public String getCoreMsg(){
        return this.msgStrData.toString();
    }



    public void generateMappedMsg(){
        this.mappedV2xMsg.clear();
        String[] strSplit = this.msgStrData.split("#");
        String strStage1 = strSplit[1];
        String[] strStage2 = strStage1.split(";");
        //In stage2 is spected strings in the format key=value
        for (String s : strStage2) {
            String[] strEntry = s.split("=");
            //System.out.println("Esta é a entrada" + Arrays.toString(strEntry));
            this.mappedV2xMsg.put(strEntry[0], strEntry[1]);
        }
    }
    public HashMap<String,String> getMappedMsg(){
        return this.mappedV2xMsg;
    }

    protected String getMsgFromStrHashMap(HashMap<String, String> mappedV2xMsg){

        StringBuilder strCoreMsg = new StringBuilder("#");
        for (Map.Entry<String, String> keyEntry : mappedV2xMsg.entrySet()) {
            strCoreMsg.append(keyEntry.getKey()).append("=").append(keyEntry.getValue()).append(";");
        }
        strCoreMsg.append("#");

        return strCoreMsg.toString();
    }



}
