package org.eclipse.mosaic.app.sdnvfn.utils;


import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.AdHocModuleConfiguration;
import org.eclipse.mosaic.fed.application.app.api.OperatingSystemAccess;
import org.eclipse.mosaic.fed.application.app.api.os.OperatingSystem;
import org.eclipse.mosaic.lib.enums.AdHocChannel;

/**
 * The NetUtils class aims to be a source of statics methods to standarize procedures related to network.
 */
public class NetUtils {

    /**
     *
     * @param unitOsAccess
     * @param radioRange
     * @param adHocChannel
     * The method instantiate AdHoc Interfaces to any unit that call this method, based on distance
     */
    public static void createAdHocInterface(OperatingSystemAccess <? extends OperatingSystem> unitOsAccess, float radioRange, AdHocChannel adHocChannel){

        unitOsAccess.getOs().getAdHocModule().enable(new AdHocModuleConfiguration()
                .camMinimalPayloadLength(200L)
                .addRadio().channel(adHocChannel).distance(radioRange).create()
        );
    }

}



