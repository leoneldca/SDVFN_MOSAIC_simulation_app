/*
 * Copyright (c) 2020 Fraunhofer FOKUS and others. All rights reserved.
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contact: mosaic@fokus.fraunhofer.de
 */

package org.eclipse.mosaic.app.sdnvfn;

import java.io.IOException;
import java.io.Serializable;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.eclipse.mosaic.app.sdnvfn.config.VehicleConfig;
import org.eclipse.mosaic.app.sdnvfn.utils.NetUtils;
import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.CamBuilder;
import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.ReceivedAcknowledgement;
import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.ReceivedV2xMessage;
import org.eclipse.mosaic.fed.application.app.ConfigurableApplication;
import org.eclipse.mosaic.fed.application.app.api.CommunicationApplication;
import org.eclipse.mosaic.fed.application.app.api.VehicleApplication;
import org.eclipse.mosaic.fed.application.app.api.os.VehicleOperatingSystem;
import org.eclipse.mosaic.interactions.communication.V2xMessageTransmission;
import org.eclipse.mosaic.lib.enums.AdHocChannel;
import org.eclipse.mosaic.lib.objects.vehicle.VehicleData;
import org.eclipse.mosaic.lib.util.SerializationUtils;
import org.eclipse.mosaic.lib.util.scheduling.Event;
import org.eclipse.mosaic.rti.TIME;

/**
 * This is a simple application that shows sending a CAM (Cooperative Awareness Message) with an additional information (user tagged value)
 * by using the {@link CamBuilder#userTaggedValue(byte[])}) method.
 * In this way an additional byte field can be sent via CAM, nevertheless this is often connected with some serious work.
 * You may also want to safely serialize / deserialize objects.
 * <p>
 * The CAMs will be sent by an ad hoc module so that only vehicles with an enabled ad hoc module can receive it.
 **/
public class CarCamSendingApp extends ConfigurableApplication<VehicleConfig,VehicleOperatingSystem> implements VehicleApplication, CommunicationApplication {

    /**
     * If the control of every byte is not needed, the
     * {@link SerializationUtils} can be used. This class converts an
     * object into a byte field and vice versa.
     */

    private VehicleConfig vhConfig;
    public static final SerializationUtils<MyComplexTaggedValue> DEFAULT_OBJECT_SERIALIZATION = new SerializationUtils<>();

    public CarCamSendingApp() {
        super(VehicleConfig.class, "VehicleConfiguration");
    }

    private static class MyComplexTaggedValue implements Serializable {
        public double latitude;
        public double longitude;
        public String id;
        public String tag;
        
        @Override
        public String toString() {
            tag = "id="+id+",Latitude=" + latitude + ", " + "Longitude=" + longitude;
            return tag;

        }
    }

    


    /**
     * Setting up the communication module and scheduling the next event for the next second.
     */
    @Override
    public void onStartup() {
        this.vhConfig = this.getConfiguration();  //load ConfigFile to config object
        NetUtils.createAdHocInterface(this,this.vhConfig.radioRange,AdHocChannel.CCH);
        getLog().infoSimTime(this, "Set up");
        //sendCam(); Don't do this here! Sending CAMs only makes
        // sense when we have access to vehicle info of sender, which is not ready at the set up stage.

        getOs().getEventManager().addEvent(getOs().getSimulationTime() + TIME.SECOND, this);
    }

    /**
     * Sending CAM and scheduling next events every second.
     */
    @Override
    public void processEvent(Event event) {
        sendCam();
        getOs().getEventManager().addEvent(getOs().getSimulationTime() + TIME.SECOND, this);
    }

    private void sendCam() {
        getLog().infoSimTime(this, "Sending CAM");
        getOs().getAdHocModule().sendCam();
    }

    @Override
    public void onMessageReceived(ReceivedV2xMessage receivedV2xMessage) {
    }

    @Override
    public void onAcknowledgementReceived(ReceivedAcknowledgement acknowledgedMessage) {
    }

    @Override
    public void onCamBuilding(CamBuilder camBuilder) {
        // this method will be triggered from the operating system (may a CAM or DENM will be prepared to send)
        // create a new object
        CarCamSendingApp.MyComplexTaggedValue taggedContent = new CarCamSendingApp.MyComplexTaggedValue();
        taggedContent.latitude = getOs().getPosition().getLatitude();
        taggedContent.longitude = getOs().getPosition().getLongitude();
        taggedContent.id = getOs().getVehicleData().getName();
        taggedContent.tag = "Hello from " + (getOs().getVehicleData() != null
                ? getOs().getVehicleData().getName()
                : "unknown vehicle"
        );

        try {
            byte[] byteArray = DEFAULT_OBJECT_SERIALIZATION.toBytes(taggedContent);
            camBuilder.userTaggedValue(byteArray);
        } catch (IOException ex) {
            getLog().error("Error during a serialization.", ex);
        }
    }

    @Override
    public void onMessageTransmitted(V2xMessageTransmission v2xMessageTransmission) {
    }

    @Override
    public void onShutdown() {
        getLog().infoSimTime(this, "Final de aplicação de transmissão de CAMs");
    }

    @Override
    public void onVehicleUpdated(@Nullable VehicleData previousVehicleData, @Nonnull VehicleData updatedVehicleData) {

    }

}
