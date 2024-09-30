package org.eclipse.mosaic.app.sdnvfn.utils;

import org.eclipse.mosaic.app.sdnvfn.information.RsuAnnouncedInfo;
import org.eclipse.mosaic.app.sdnvfn.message.GenericV2xMessage;
import org.eclipse.mosaic.fed.application.app.api.Application;
import org.eclipse.mosaic.fed.application.app.api.OperatingSystemAccess;
import org.eclipse.mosaic.fed.application.app.api.os.OperatingSystem;
import org.eclipse.mosaic.lib.objects.v2x.etsi.Cam;
import org.eclipse.mosaic.lib.util.scheduling.Event;
import org.eclipse.mosaic.rti.TIME;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;

public class IntraUnitAppInteractor {

    OperatingSystemAccess<? extends OperatingSystem> unitOsAccess;
    private final List<? extends Application> applicationList;
    private final HashMap<String,Application> appMap;
    public IntraUnitAppInteractor(OperatingSystemAccess<? extends OperatingSystem> unitOsAccess) {
        this.unitOsAccess = unitOsAccess;
        this.applicationList = unitOsAccess.getOs().getApplications();
        this.appMap = new HashMap<>();
        this.convertAppListToMap();

    }

    private void convertAppListToMap(){
        for (Application application : this.applicationList) {
            //Criar uma hash com as aplicações da Unidade
            //Inserir cada aplicação na Hash
            String[] strSplit = application.toString().split("@");
            this.appMap.put(strSplit[0],application);
        }
    }

    public void sendV2xMsgToApp(@NotNull GenericV2xMessage v2xMessage, Class appClass){
        //Comunicando com outra aplicação via EventProcess
        final Event sendIntraUnitMsg = new Event(this.unitOsAccess.getOs().getSimulationTime()+TIME.MILLI_SECOND*2,this.appMap.get(appClass.getName()),v2xMessage);
        this.unitOsAccess.getOs().getEventManager().addEvent(sendIntraUnitMsg);
    }

    public void sendStrToApp(String strMsg, Class appClass){
        //Comunicando com outra aplicação via EventProcess
        final Event sendIntraUnitMsg = new Event(unitOsAccess.getOs().getSimulationTime()+TIME.MILLI_SECOND*2,this.appMap.get(appClass.getName()),strMsg);
        this.unitOsAccess.getOs().getEventManager().addEvent(sendIntraUnitMsg);
    }

    public void sendRsuAnnouncedInfoToApp(RsuAnnouncedInfo rsuAnnouncedInfo,Class appClass){
        final Event event = new Event(unitOsAccess.getOs().getSimulationTime()+TIME.MILLI_SECOND*2,this.appMap.get(appClass.getName()), rsuAnnouncedInfo);
        this.unitOsAccess.getOs().getEventManager().addEvent(event);
    }

    public void sendCamMsgToApp(Cam camMessage, Class appClass){
        //Comunicando com outra aplicação via EventProcess
        final Event sendIntraUnitMsg = new Event(this.unitOsAccess.getOs().getSimulationTime()+TIME.MILLI_SECOND*2,this.appMap.get(appClass.getName()),camMessage);
        this.unitOsAccess.getOs().getEventManager().addEvent(sendIntraUnitMsg);
    }

}
