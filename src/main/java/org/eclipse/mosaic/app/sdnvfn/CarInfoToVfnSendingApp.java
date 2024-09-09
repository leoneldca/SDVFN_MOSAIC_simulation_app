package org.eclipse.mosaic.app.sdnvfn;
import org.eclipse.mosaic.app.sdnvfn.config.VehicleConfig;

import org.eclipse.mosaic.app.sdnvfn.information.RsuAnnouncedInfo;
import org.eclipse.mosaic.app.sdnvfn.message.CarInfoToVfnMsg;
import org.eclipse.mosaic.app.sdnvfn.network.CommunicationInterface;
import org.eclipse.mosaic.fed.application.app.ConfigurableApplication;
import org.eclipse.mosaic.fed.application.app.api.VehicleApplication;
import org.eclipse.mosaic.fed.application.app.api.os.VehicleOperatingSystem;

import org.eclipse.mosaic.lib.objects.vehicle.VehicleData;
import org.eclipse.mosaic.lib.util.scheduling.Event;
import org.eclipse.mosaic.lib.enums.AdHocChannel;
import org.eclipse.mosaic.lib.objects.v2x.MessageRouting;

import org.eclipse.mosaic.rti.TIME;
import org.jetbrains.annotations.NotNull;

import java.lang.String;
import java.util.Objects;

public class CarInfoToVfnSendingApp extends ConfigurableApplication<VehicleConfig,VehicleOperatingSystem> implements VehicleApplication {

    public CarInfoToVfnSendingApp() {
        super(VehicleConfig.class, "VehicleConfiguration");
    }
    private final static long MESSAGE_TIME_INTERVAL = 2*TIME.SECOND;
    private String vhId;
    private String rsuAccessPointId;
    private VehicleConfig vehicleConfig;

    private CommunicationInterface communicationInterface;


    @Override
    public void onStartup() {
        this.vehicleConfig = this.getConfiguration();
        this.rsuAccessPointId = "null";
        communicationInterface = new CommunicationInterface(this);
        this.vhId = getOs().getId();

        //getLog().infoSimTime(this, "Activating Adhoc Communication");
        // Ativação do módulo de comunicação Adhoc
        communicationInterface.createAdHocInterface(this.vehicleConfig.radioRange,AdHocChannel.CCH);
        schedulingInfoSending();//está comentada
    }

    public void schedulingInfoSending() {
        Event event = new Event(getOs().getSimulationTime() + MESSAGE_TIME_INTERVAL, this,"send");
        getOs().getEventManager().addEvent(event);
        //getLog().info("Evento de envio de mensagens foi criado com Sucesso");
    }



    @Override
    public void processEvent(Event event) {
        Object resource = event.getResource();
        if(resource instanceof String){
            String msg = (String) resource;
            if(msg.startsWith("rsu_")){
                //change the RSU V2X message receiver.
                getLog().info("recebi informação do RSU via String");
                rsuAccessPointId = msg;
                schedulingInfoSending();
            }
            if(msg.equals("send")){

                if(!Objects.equals(this.rsuAccessPointId, "null")) {
                    sendInfoToVFN();
                }
                schedulingInfoSending();
            }
        }else if(resource instanceof RsuAnnouncedInfo){
            RsuAnnouncedInfo rsuAnnouncedInfo = (RsuAnnouncedInfo) resource;
            this.rsuAccessPointId = rsuAnnouncedInfo.getRsuId();
            schedulingInfoSending(); //enviar informação para a SDVFN para registrar a mudança
            //sendInfoToVFN();
            //recebe a atualização da RSU AccessPoint por meio da aplicação CarV2xInterfaceApp
            //getLog().info("recebi informação do RSU via Objeto RsuAnnouncedInfo");
        }
    }

    public void sendInfoToVFN() {
        String vehicleStrData = getVehicleStrData();
        final MessageRouting msgRoute = 
                getOs().getAdHocModule().createMessageRouting().viaChannel(AdHocChannel.CCH).topoCast(rsuAccessPointId,this.vehicleConfig.defautMsgHops);
        final CarInfoToVfnMsg infoToFogMsg = new CarInfoToVfnMsg(msgRoute,vehicleStrData); //VehicleInfoToVfnMsg extends V2xMessage
        communicationInterface.sendAdhocV2xMessage(infoToFogMsg);
        //getOs().getAdHocModule().sendV2xMessage(infoToFogMsg);
        //getLog().infoSimTime(this, "CarInfoToVfnMsg via {}", this.rsuAccessPointId);
    }

    public String getVehicleStrData(){
        String vehicleStrData;
        vehicleStrData = "#vhId="+this.vhId+
                ";msgType=carBeaconToVFN"+
                ";sendTime="+getOs().getSimulationTime()+
                ";netDestAddress="+this.vehicleConfig.rsuNet+
                ";unitDestId="+this.rsuAccessPointId+
                ";latitude="+getOs().getVehicleData().getPosition().getLatitude() +
                ";longitude="+getOs().getVehicleData().getPosition().getLongitude()+
                ";speed="+getOs().getVehicleData().getSpeed()+
                ";heading="+getOs().getVehicleData().getHeading().toString()+
                ";aceleration="+getOs().getVehicleData().getLongitudinalAcceleration().toString()+
                ";rsuId="+this.rsuAccessPointId+
                "#";

        return vehicleStrData;
    }






    @Override
    public void onVehicleUpdated(VehicleData previousVehicleData, @NotNull VehicleData updatedVehicleData) {
        //getLog().infoSimTime(this, "Vehicle Information: Id {} ", getOs().getId());
        //getLog().infoSimTime(this, "Vehicle Information: Latitude {} ",updatedVehicleData.getPosition().getLatitude());
        //getLog().infoSimTime(this, "Vehicle Information: Longitude {} ", updatedVehicleData.getPosition().getLongitude());
        //getLog().infoSimTime(this, "Vehicle Information: ProjectedPosition {} ", updatedVehicleData.getProjectedPosition());

        //this.vhLatitude = updatedVehicleData.getPosition().getLatitude();
       //this.vhLongitude = updatedVehicleData.getPosition().getLongitude();
        // this.vhSpeed = updatedVehicleData.getSpeed();

    }

    @Override
    public void onShutdown() {
        getLog().info("Ending CarInfoToVFNSendingApp");
    }

}
