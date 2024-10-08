package org.eclipse.mosaic.app.sdnvfn.information;

import org.eclipse.mosaic.app.sdnvfn.network.NetworkNode;
import org.eclipse.mosaic.app.sdnvfn.utils.NodesUtils;
import org.eclipse.mosaic.lib.geo.MutableGeoPoint;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;

/**
 * A Classe armazenada dados recebidos de um único veículo, contendo ID, Speed, Latitude, Longitude. T
 */
public class VfnConnectedVehicle extends RsuConnectedVehicle{

    private ArrayList<NetworkNode> lastRsuRunnerPath;



    public VfnConnectedVehicle() {
        super();
        this.lastRsuRunnerPath = new ArrayList<>();
    }


    /**
     *
     * @return retorna um Array de NetWorkNodes que compõe o caminho entre o RSU-AP e o Rsu-Runner
     */

    public ArrayList<NetworkNode> getActualRsuRunnerPath(){
        return  this.actualRsuRunnerPath;
    }


    public void setActualRsuRunnerPath(ArrayList<NetworkNode> actualRsuRunnerPath){
        this.actualRsuRunnerPath.clear();
        this.actualRsuRunnerPath.addAll(actualRsuRunnerPath);
    }


    public ArrayList<NetworkNode> getlastRsuRunnerPath(){
        return  this.lastRsuRunnerPath;
    }
    public void setLastRsuRunnerPath(ArrayList<NetworkNode> lastRsuRunnerPath){
        this.lastRsuRunnerPath.clear();
        this.lastRsuRunnerPath.addAll(lastRsuRunnerPath);
    }


    /**
     *
     * @return um Array de NetworkNodes que compõe o caminho entre próximo-RSU-AP e o RSU-Runner
     */
    public ArrayList<NetworkNode> getNextRsuRunnerPath(){
        return this.nextRsuRunnerPath;
    }


    public void setNextRsuRunnerPath(ArrayList<NetworkNode> nextRsuRunnerPath){
        this.nextRsuRunnerPath.clear();
        this.nextRsuRunnerPath.addAll(nextRsuRunnerPath);
    }


    /**
     * Este método deve operacionalizar o registro do caminho que será utilizado entre o próximo RSU Access Point para o RSURunner
     * @param newRsuPredictedPath deve conter o novo para caminho entre o novo RSUAP o mesmo RSU Runner.
     *
     */

    public void updateRsuRunnerNextPath(ArrayList<NetworkNode> newRsuPredictedPath){
        this.nextRsuRunnerPath.clear();
        this.nextRsuRunnerPath = newRsuPredictedPath;

    }

    @Override
    public String getRsuOfservice(String serviceId){
        if (serviceRsuMap.containsKey(serviceId)){
            return serviceRsuMap.get(serviceId);
        }
        return "-1";
    }
}
