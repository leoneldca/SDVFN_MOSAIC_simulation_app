package org.eclipse.mosaic.app.sdnvfn.network;

import org.eclipse.mosaic.app.sdnvfn.config.ServerConfig;
import org.eclipse.mosaic.app.sdnvfn.information.VfnConnectedVehicle;
import org.eclipse.mosaic.app.sdnvfn.utils.NodesUtils;
import org.eclipse.mosaic.lib.geo.MutableGeoPoint;
import org.eclipse.mosaic.app.sdnvfn.utils.HeadingCalculator;

import java.util.HashMap;

public class RsuPredictor {

    private HashMap<String, MutableGeoPoint> rsuPositionMap;
    private VfnConnectedVehicle connectedVehicle;
    private static final int MAX_HEADING_DIFFERENCE = 180;
    private static final float HEADING_DIFFERENCE_WEIGHT = 1F;
    private ServerConfig serverconfig;


    public RsuPredictor(HashMap<String, MutableGeoPoint> rsuPositionMap, ServerConfig serverConfig) {
        this.rsuPositionMap = rsuPositionMap;
        this.serverconfig = serverConfig;
    }

    /**
     * Método realiza a predição do próximo RSU-AP no qual o veículo irá se conectar.
     * @param connectedVehicle Recebe como parâmetro o objeto veículo
     * @return retorna um objeto String que contem o Predicted-Next-RSU-AP
     */
    public String predictNextRsuToVehicle(VfnConnectedVehicle connectedVehicle) {
        this.connectedVehicle = connectedVehicle;

        if(this.rsuPositionMap.size()<2 ){ //Seria o caso de um mapa com apenas 1 RSU, manter sempre o mesmo.
            return connectedVehicle.getRsuApId();
        }
        double vehicleHeading =this.connectedVehicle.getHeading();
        double headingDiference;
        headingDiference = HeadingCalculator.calculateHeadingDifference(vehicleHeading, this.connectedVehicle.getLatitude(),this.connectedVehicle.getLongitude(),
                this.rsuPositionMap.get(this.connectedVehicle.getRsuApId()).getLatitude(),this.rsuPositionMap.get(this.connectedVehicle.getRsuApId()).getLongitude());

        if(headingDiference<=serverconfig.maxHeadingDifferenceList2 && (connectedVehicle.getActualDistanceToRsu()>=serverconfig.adHocRadioRange*serverconfig.handoverPredictionZoneMultiplier)) {
            return connectedVehicle.getRsuApId(); ////Se ainda está se aproximando do RSU-AP atual, mantem. Se distancia é menor que 70% da máxima, mantém. Se os dois caso acontecem, mantem
        }//se está se distanciando e a distância é maior de 70% da máxima, então realizar predição
        double minTimeToReachRsu = Double.MAX_VALUE;
        String predictedRSUId = this.connectedVehicle.getRsuApId(); //Num primeiro momento assumir que o proximo RSU-AP será o atual.

        //double targetHeading;


        for (HashMap.Entry<String, MutableGeoPoint>  rsu : this.rsuPositionMap.entrySet()) {
            //relativeBearing = NodesUtils.calculateRelativeBearing(this.connectedVehicle.getLatitude(), this.connectedVehicle.getLongitude(),
            double distance = NodesUtils.calculateDistanceBetweenNodes(this.connectedVehicle.getLatitude(), this.connectedVehicle.getLongitude(),rsu.getValue().getLatitude(), rsu.getValue().getLongitude());
            if(distance<=serverconfig.adHocRadioRange*serverconfig.handoverPredictionZoneMultiplier){ //Apenas RSUs que estão antes das zonas de handover
                //targetHeading = GeoUtils.azimuth( new MutableGeoPoint(this.connectedVehicle.getLatitude(),this.connectedVehicle.getLongitude()), new MutableGeoPoint(rsu.getValue().getLatitude(), rsu.getValue().getLongitude()));
                headingDiference = HeadingCalculator.calculateHeadingDifference(vehicleHeading, this.connectedVehicle.getLatitude(),this.connectedVehicle.getLongitude(), rsu.getValue().getLatitude(),rsu.getValue().getLongitude());

                double relativeSpeed = calculateRelativeSpeed(headingDiference);

                double timeToReachRsu = distance / relativeSpeed;

                if (timeToReachRsu < minTimeToReachRsu) { //Seleciona a menor distância, ponderada pela relative speed ( a qual diminue com o aumento da heading difference)
                    minTimeToReachRsu = timeToReachRsu;
                    predictedRSUId = rsu.getKey();
                }

            }

        }
        return predictedRSUId; //connectedVehicle.getRsuApId(); // //connectedVehicle.getRsuApId(); //sem predição //
    }

    private double calculateRelativeSpeed(double headingDiference){
        // Assuming a simple linear relationship between heading difference and speed
        //
        return 180-(HEADING_DIFFERENCE_WEIGHT*headingDiference);
    }


}
