package org.eclipse.mosaic.app.sdnvfn;

import org.eclipse.mosaic.app.sdnvfn.information.RsuAnnouncedInfo;
import org.eclipse.mosaic.app.sdnvfn.message.CarInfoToVfnMsg;
import org.eclipse.mosaic.fed.application.app.AbstractApplication;
import org.eclipse.mosaic.fed.application.app.api.VehicleApplication;
import org.eclipse.mosaic.fed.application.app.api.os.VehicleOperatingSystem;

import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.AdHocModuleConfiguration;
import org.eclipse.mosaic.lib.objects.vehicle.VehicleData;
import org.eclipse.mosaic.lib.util.scheduling.Event;
import org.eclipse.mosaic.lib.enums.AdHocChannel;
import org.eclipse.mosaic.lib.objects.v2x.MessageRouting;

import org.eclipse.mosaic.rti.TIME;
import org.jetbrains.annotations.NotNull;

import java.awt.Color;
import java.lang.String;
import java.util.Objects;

public class CarInfoToVfnSendingApp extends AbstractApplication<VehicleOperatingSystem> implements VehicleApplication {

    private final static long MESSAGE_TIME_INTERVAL = 2*TIME.SECOND;
    private String vhId;
    private String rsuAccessPointId = "null";
    //private final byte[] ipv4ServerAddress = {10,5,0,0};
    private int hops = 10;


    private double vhLatitude;
    private double vhLongitude;
    private double vhSpeed;

    public void sendInfoToVFN() {
        String vehicleStrData = getVehicleStrData();
        final MessageRouting msgRoute = 
                getOs().getAdHocModule().createMessageRouting().viaChannel(AdHocChannel.CCH).topoCast(rsuAccessPointId,hops);
        final CarInfoToVfnMsg infoToFogMsg = new CarInfoToVfnMsg(msgRoute,vehicleStrData); //VehicleInfoToVfnMsg extends V2xMessage
        getOs().getAdHocModule().sendV2xMessage(infoToFogMsg);
        getLog().infoSimTime(this, "Send Vehicle InfoMsg to VFN via {}", this.rsuAccessPointId);
    }

    public String getVehicleStrData(){
        String vehicleStrData;
        vehicleStrData = "#vhId="+this.vhId+
                ";latitude="+this.vhLatitude +
                ";longitude="+this.vhLongitude+
                ";speed="+this.vhSpeed+
                "#";
        return vehicleStrData;
    }

    public void schedulingInfoSending() {
        Event event = new Event(getOs().getSimulationTime() + MESSAGE_TIME_INTERVAL, this,"send");
        getOs().getEventManager().addEvent(event);
        getLog().info("Evento de envio de mensagens foi criado com Sucesso");
    }

    @Override
    public void onStartup() {
        this.vhId = getOs().getId();
        this.vhSpeed = 0D;
        //System.out.println("Latitude:" + getOs().getPosition().getLongitude());
        this.vhLatitude = getOs().getPosition().getLatitude();
        this.vhLongitude = getOs().getPosition().getLongitude();

        getLog().infoSimTime(this, "Activating Adhoc Communication");
        // Ativação do módulo de comunicação Adhoc
        getOs().getAdHocModule().enable(new AdHocModuleConfiguration()
                    .addRadio()
                    .channel(AdHocChannel.CCH)
                    .distance(50.0)
                    .create());

        schedulingInfoSending();
    }

    // método a ser invocado no momento do evento criado no método on startup.
    // É necessário criar novo evento sempre após a execução deste.
    @Override
    public void processEvent(Event event) {
        Object resource = event.getResource();
        if(resource instanceof String){
            String msg = (String) resource;
            if(msg.startsWith("rsu_")){
                //change the RSU V2X message receiver.
                getLog().info("recebi informação do RSU via String");
                rsuAccessPointId = msg;
            }
            if(msg.equals("send")){

                if(!Objects.equals(rsuAccessPointId, "null")) {
                    sendInfoToVFN();
                    getOs().requestVehicleParametersUpdate()
                            .changeColor(Color.GREEN)
                            .apply();
                }
                schedulingInfoSending();
            }
        }else if(resource instanceof RsuAnnouncedInfo){
            RsuAnnouncedInfo rsuAnnouncedInfo = (RsuAnnouncedInfo) resource;
            rsuAccessPointId = rsuAnnouncedInfo.getRsuId();
            getLog().info("recebi informação do RSU via Objeto RsuAnnouncedInfo");
        }
    }

    @Override
    public void onVehicleUpdated(VehicleData previousVehicleData, @NotNull VehicleData updatedVehicleData) {
        //getLog().infoSimTime(this, "Vehicle Information: Id {} ", getOs().getId());
        //getLog().infoSimTime(this, "Vehicle Information: Latitude {} ",updatedVehicleData.getPosition().getLatitude());
        //getLog().infoSimTime(this, "Vehicle Information: Longitude {} ", updatedVehicleData.getPosition().getLongitude());
        //getLog().infoSimTime(this, "Vehicle Information: Speed {} ", updatedVehicleData.getSpeed());

        this.vhLatitude = updatedVehicleData.getPosition().getLatitude();
        this.vhLongitude = updatedVehicleData.getPosition().getLongitude();
        this.vhSpeed = updatedVehicleData.getSpeed();

    }

    @Override
    public void onShutdown() {
        getLog().info("Ending Vehicle Data do VFN sendingApp");
    }

}
