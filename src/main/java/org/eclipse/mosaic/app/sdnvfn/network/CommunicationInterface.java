package org.eclipse.mosaic.app.sdnvfn.network;

import org.eclipse.mosaic.app.sdnvfn.message.GenericV2xMessage;
import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.AdHocModuleConfiguration;
import org.eclipse.mosaic.fed.application.app.api.OperatingSystemAccess;
import org.eclipse.mosaic.fed.application.app.api.os.OperatingSystem;
import org.eclipse.mosaic.lib.enums.AdHocChannel;

public class CommunicationInterface {
    /**
     *
     */
    int sequenceNumber = 0;

    OperatingSystemAccess<? extends OperatingSystem> unitOsAccess;

    public CommunicationInterface(OperatingSystemAccess<? extends OperatingSystem> unitOsAccess){
        this.unitOsAccess = unitOsAccess;
    }

    public void createAdHocInterface(float radioRange, AdHocChannel adHocChannel){

        if(!unitOsAccess.getOs().getAdHocModule().isEnabled()){
            this.unitOsAccess.getOs().getAdHocModule().enable(new AdHocModuleConfiguration()
                    .camMinimalPayloadLength(200L)
                    .addRadio().channel(adHocChannel).distance(radioRange).create()
            );
        }

    }
    public boolean isAdhocEnable(){
        return this.unitOsAccess.getOs().getAdHocModule().isEnabled();
    }

    public void sendAdhocV2xMessage(GenericV2xMessage msg){

        //msg.setSequenceNumber(sequenceNumber);
        unitOsAccess.getOs().getAdHocModule().sendV2xMessage(msg);
        //sequenceNumber = sequenceNumber +1;
    }

    public void createCellInterface(){
        this.unitOsAccess.getOs().getCellModule().enable();
    }

    public boolean isCellEnable(){
        return this.unitOsAccess.getOs().getCellModule().isEnabled();
    }

    public void sendCellV2xMessage(GenericV2xMessage msg){
        unitOsAccess.getOs().getCellModule().sendV2xMessage(msg);
    }

}
