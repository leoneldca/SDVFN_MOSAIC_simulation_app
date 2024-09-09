package org.eclipse.mosaic.app.sdnvfn.network;

import org.eclipse.mosaic.lib.geo.MutableGeoPoint;

import java.util.HashMap;
import java.util.Objects;

public class NetworkNode {

    private String rsuId;
    private final HashMap<String,String> flowTableMap;///Mapeia um destino e o próximo hop
    private MutableGeoPoint rsuGeoPoint;

    public void setRsuGeoPoint(MutableGeoPoint rsuGeoPoint) {
        this.rsuGeoPoint = rsuGeoPoint;
    }

    public NetworkNode(String rsuId, MutableGeoPoint rsuGeoPoint) {
        setRsuId(rsuId);
        this.flowTableMap = new HashMap<>();
        this.rsuGeoPoint = rsuGeoPoint;
    }

    public HashMap<String, String> getFlowTableMap() {
        return flowTableMap;
    }

    public String getRsuId() {
        return this.rsuId;
    }
    private void setRsuId(String rsuId) {
        this.rsuId = rsuId;
    }

    public void addFlowEntry(String machingFields, String actions){
        //tratar a entrada para que fique como nos switches RSUs
        //Se as entradas já estiverem nos nós do grafo, então já estão nas RSUs e não devem ser reinseridas
        if(!this.flowTableMap.containsKey(machingFields)){
            this.flowTableMap.put(machingFields,actions);
        }else{
            //se as actions forem iguais não inserir, se as actions forem diferentes, sobrepor
            if(!Objects.equals(this.flowTableMap.get(machingFields), actions)){
                this.flowTableMap.replace(machingFields,actions);
            }
        }
    }

    public String printNode(){

        return "\nNetworkNode: Id: "+this.getRsuId()+
                "\nFlowTableEntries: "+this.flowTableMap.toString()+
                "\nRsuGeoPoint: "+this.rsuGeoPoint.toString();

    }
}
