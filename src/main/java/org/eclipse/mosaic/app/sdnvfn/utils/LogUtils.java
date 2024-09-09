package org.eclipse.mosaic.app.sdnvfn.utils;


import org.eclipse.mosaic.app.sdnvfn.information.RsuConnectedVehicle;
import org.eclipse.mosaic.app.sdnvfn.information.VfnConnectedVehicle;
import org.eclipse.mosaic.fed.application.ambassador.util.UnitLogger;
import org.eclipse.mosaic.fed.application.app.api.OperatingSystemAccess;
import org.eclipse.mosaic.fed.application.app.api.os.OperatingSystem;
import org.eclipse.mosaic.fed.application.app.api.os.RoadSideUnitOperatingSystem;
import org.eclipse.mosaic.fed.application.app.api.Application;

import java.util.HashMap;
import java.util.Map;

public class LogUtils {

    public static void mappedMsgLog(OperatingSystemAccess <? extends OperatingSystem> unitOsAccess, UnitLogger logger, HashMap<String,String> mappedStrMsg, String title){
        String strMsg ="";
        for(Map.Entry<String, String> keyEntry : mappedStrMsg.entrySet()){
           strMsg = strMsg.concat(keyEntry.getKey()+" = " + keyEntry.getValue()+"\n");
        }
        logger.info("---------------------------{}---------------------\n{}----------------------------------------------------\n",title,strMsg);

    }

    public static void waitingListLog(OperatingSystemAccess <? extends OperatingSystem> unitOsAccess, UnitLogger logger, HashMap<String,String> waitingList, String title){
        String strMsg ="";
        for(Map.Entry<String, String> keyEntry : waitingList.entrySet()){
            strMsg = strMsg.concat(keyEntry.getKey()+" = " + keyEntry.getValue()+"\n");
        }
        logger.info("---------------------------{}---------------------\n{}----------------------------------------------------\n",title,strMsg);

    }

    public static void vehicleDMLog(OperatingSystemAccess <? extends OperatingSystem> unitOsAccess, UnitLogger logger, HashMap<String, RsuConnectedVehicle> mappedVehicles, String title){

        logger.info("---------------------------{}---------------------",title);
        RsuConnectedVehicle vehicleInfo;
        for (String key: mappedVehicles.keySet()){
            vehicleInfo = mappedVehicles.get(key);
            logger.info(key+" = "+vehicleInfo.toString()+"");
        }
        //logger.info("----------------------------------------------\n\n\n");
    }

    public static void vehicleGDMLog(OperatingSystemAccess <? extends OperatingSystem> unitOsAccess, UnitLogger logger, HashMap<String, VfnConnectedVehicle> mappedVehicles, String title){

        logger.info("---------------------------{}---------------------",title);
        RsuConnectedVehicle vehicleInfo;
        for (String key: mappedVehicles.keySet()){
            vehicleInfo = mappedVehicles.get(key);
            logger.info(key+" = "+vehicleInfo.toString()+"");
        }
        //logger.info("----------------------------------------------\n\n\n");
    }

}



