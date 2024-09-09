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
    private String vhId;
    private String rsuId;
    private String lastRsuId;
    private String nextRsuId;
    private Double speed;
    private Double latitude;
    private Double longitude;
    private Double heading;
    private Double aceleration;
    private long infoSentTime;
    private long lastInfoSentTime;
    private Double lastDistanceToRsu;
    private Double distanceToRsu;
    private final HashMap<String, String> serviceRsuMap;  //Map a service to a RSU
    private ArrayList<NetworkNode> actualRsuRunnerPath;
    private ArrayList<NetworkNode> lastRsuRunnerPath;
    private ArrayList<NetworkNode> nextRsuRunnerPath;



    public VfnConnectedVehicle() {
        this.vhId = "";
        this.rsuId = "";
        this.lastRsuId = "";
        this.nextRsuId = "";
        this.speed = 0D;
        this.heading = 0D;
        this.latitude = 0D;
        this.longitude = 0D;
        this.lastInfoSentTime = 0L;
        this.lastDistanceToRsu = Double.MAX_VALUE;
        this.distanceToRsu = Double.MAX_VALUE;
        this.serviceRsuMap = new HashMap<>(); //sempre que um novo veículo é adicionado, obrigatoriamente ele terá uma lista de serviços
        this.actualRsuRunnerPath = new ArrayList<>();
        this.nextRsuRunnerPath = new ArrayList<>();
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
    public void updateVehicleData(HashMap<String, String> mappedMsg){
        this.setLastInfoSentTime(this.getInfoSentTime()); //tempo de envio da informação passa a ser a anterior
        this.setInfoSentTime(Long.parseLong(mappedMsg.get("sendTime")));
        this.setVehicleId(mappedMsg.get("vhId"));
        this.setSpeed(Double.valueOf(mappedMsg.get("speed")));
        this.setHeading(Double.valueOf(mappedMsg.get("heading")));
        this.setAceleration(Double.valueOf(mappedMsg.get("aceleration")));
        this.setLatitude(Double.valueOf(mappedMsg.get("latitude")));
        this.setLongitude(Double.valueOf(mappedMsg.get("longitude")));

        if(!Objects.equals(getRsuApId(), mappedMsg.get("rsuId"))){
            //Se for atualizado para uma RSU diferente, ajustar a distância anterior para a maior possível.
            setLastDistanceToRsu(Double.MAX_VALUE);
        }else{
            setLastDistanceToRsu(getActualDistanceToRsu()); //Se está na mesma RSU, armazena a última distãncia antes de atualizar.
        }
        this.setLastRsuApId(this.getRsuApId());
        this.setRsuApId(mappedMsg.get("rsuId"));
    }

    @Override
    public String getRsuApId() {
        return rsuId;
    }

    /**
     * Atualizar o RSU-ID atual significa atualizar o RSU no qual o veículo se conecta.
     * O método recebe um novo RSU-ID. Antes de atualizar, o atual RSU-ID passa a ser o last-RSU-ID.
     * @param newRsuId
     */
    @Override
    public void setRsuApId(String newRsuId){
        if(Objects.equals(this.rsuId, "")){
            this.lastRsuId = this.rsuId;
            this.rsuId = newRsuId;

        }else{
            this.lastRsuId = this.rsuId;
            this.rsuId = newRsuId;
        }


    }

    public String getLastRsuApId(){
        return lastRsuId;
    }
    public void setLastRsuApId(String lastRsuId){

         this.lastRsuId = lastRsuId;
    }

    public String getNextRsuId() {
        return nextRsuId;
    }

    public void setNextRsuApId(String nextRsuId) {
        this.nextRsuId = nextRsuId;
    }

    public String getVechicleId() {
        return vhId;
    }
    public void setVehicleId(String vhId){
        this.vhId = vhId;
    }

    public double getSpeed() {
        return speed;
    }

    public  void setSpeed(double speed){
        this.speed = speed;
    }

    public double getHeading() {
        return heading;
    }

    public void setHeading(double heading) {
        this.heading = heading;
    }

    public Double getAceleration() {
        return aceleration;
    }

    public void setAceleration(Double aceleration) {
        this.aceleration = aceleration;
    }

    public long getInfoSentTime() {
        return infoSentTime;
    }

    public void setInfoSentTime(long infoSentTime) {
        this.infoSentTime = infoSentTime;
    }


    public long getLastInfoSentTime() {
        return lastInfoSentTime;
    }

    public void setLastInfoSentTime(long lastInfoSentTime) {
        this.lastInfoSentTime = lastInfoSentTime;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude){
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude){
        this.longitude = longitude;
    }


    public Double getActualDistanceToRsu() {return distanceToRsu;
    }

    public void setDistanceVhToRsu(MutableGeoPoint rsuGeoPoint){
        this.lastDistanceToRsu=this.getActualDistanceToRsu();
        this.distanceToRsu = NodesUtils.calculateVehicleRsuDistance(new MutableGeoPoint(getLatitude(),getLongitude()),rsuGeoPoint);
    }

    public Double getLastDistanceToRsu() {
        return this.lastDistanceToRsu;
    }

    public void setLastDistanceToRsu(Double lastDistanceToRsu) {
        this.lastDistanceToRsu = lastDistanceToRsu;
    }



    public HashMap<String, String> getServiceRsuMap() {
        return serviceRsuMap;
    }

    public void addService(String serviceId, String rsuId){
        this.serviceRsuMap.put(serviceId,rsuId); //adiciona o serviço.
    }

    public Boolean isServiceMapEmpty(){
        return serviceRsuMap.isEmpty();
    }


    public void removeVehicleService(String serviceId){
        serviceRsuMap.remove(serviceId);
    }

    public void setRsuServiceRunner(String serviceId, String rsuRunnerId){

        if(this.serviceRsuMap.containsKey(serviceId)){
            serviceRsuMap.replace(serviceId,rsuRunnerId);
        }else{
            serviceRsuMap.put(serviceId,rsuRunnerId);
        }

    }

    public String getRsuOfservice(String serviceId){
        if (this.serviceRsuMap.containsKey(serviceId)){
            return serviceRsuMap.get(serviceId);
        }
        return "-1";
    }



    @Override
    public String toString() {
        return "RsuConnectedVehicle{" +
                "vhId='" + this.vhId + '\'' +
                ", rsuId=" + this.rsuId +
                ", speed=" + this.getSpeed() +
                ", heading=" + this.getHeading() +
                ", aceleration=" + this.getAceleration() +
                ", latitude=" + this.getLatitude() +
                ", longitude=" + this.getLongitude() +
                ", services=" + this.serviceRsuMap.toString() +
                '}';
    }
}
