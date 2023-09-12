package org.eclipse.mosaic.app.sdnvfn.utils;


import org.eclipse.mosaic.fed.application.ambassador.util.UnitLogger;
import org.eclipse.mosaic.fed.application.app.api.OperatingSystemAccess;
import org.eclipse.mosaic.fed.application.app.api.os.OperatingSystem;
import org.eclipse.mosaic.fed.application.app.api.os.RoadSideUnitOperatingSystem;
import org.eclipse.mosaic.fed.application.app.api.Application;

import java.util.HashMap;
import java.util.Map;

public class LogUtils {

    public static void mappedMsgLog(OperatingSystemAccess <? extends OperatingSystem> unitOsAccess, UnitLogger logger, HashMap<String,String> mappedStrMsg, String title){

        logger.infoSimTime(unitOsAccess,"---------------------------{}---------------------",title);
        for(Map.Entry<String, String> keyEntry : mappedStrMsg.entrySet()){
            logger.info("{} = {}",keyEntry.getKey(), keyEntry.getValue());
        }
        logger.info("----------------------------------------------------");
    }

}



