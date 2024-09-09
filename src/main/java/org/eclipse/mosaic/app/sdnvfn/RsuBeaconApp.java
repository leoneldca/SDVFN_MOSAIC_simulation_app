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

import org.eclipse.mosaic.app.sdnvfn.config.RsuConfig;
import org.eclipse.mosaic.app.sdnvfn.message.RsuBeaconV2xMsg;
import org.eclipse.mosaic.app.sdnvfn.utils.IntraUnitAppInteractor;
import org.eclipse.mosaic.fed.application.app.ConfigurableApplication;
import org.eclipse.mosaic.fed.application.app.api.os.RoadSideUnitOperatingSystem;
import org.eclipse.mosaic.lib.enums.AdHocChannel;
import org.eclipse.mosaic.lib.objects.v2x.MessageRouting;
import org.eclipse.mosaic.lib.util.scheduling.Event;
import org.eclipse.mosaic.rti.TIME;

/**
 * Aplicação de envio de broadcast de beacons para anúncio de RSU aos veículos
 * Esta aplicação não recebe mensagens provindas da rede. Apenas envia
 */
public class RsuBeaconApp extends ConfigurableApplication<RsuConfig,RoadSideUnitOperatingSystem> {

    private long beaconTimeInterval;
    private RsuConfig rsuConfig;
    private IntraUnitAppInteractor intraUnitAppInteractor;


    public RsuBeaconApp() {
        super(RsuConfig.class, "RsuConfiguration");
    }

    /**
     *
     */
    @Override
    public void onStartup() {

        this.rsuConfig = this.getConfiguration();  ///load ConfigFile into config object
        beaconTimeInterval = this.rsuConfig.beaconInterval*TIME.SECOND; ///intervalo ente beacons é definido via arquivo de configuração
        intraUnitAppInteractor = new IntraUnitAppInteractor(this);

        adhocBeaconScheduler();

    }
    public String getRsuBeaconData(){
        String rsuStrData;
        rsuStrData = "#msgType="+this.rsuConfig.beaconMsgType+
                ";netDestAddress="+this.rsuConfig.vehicleNet+
                ";rsuId="+getOs().getId()+
                ";latitude="+getOs().getPosition().getLatitude() +
                ";longitude="+getOs().getPosition().getLongitude()+
                "#";
        return rsuStrData;
    }

    /**
     * Repassa para a aplicação Switch o beacon RSU para ser enviado para os veículos via topoBroadcast
     */
    public void adhocBeaconSender(){
        final MessageRouting routing =
                getOs().getAdHocModule().createMessageRouting().viaChannel(AdHocChannel.CCH).topoBroadCast(rsuConfig.defautMsgHops);
        final RsuBeaconV2xMsg beaconV2xMsg = new RsuBeaconV2xMsg(routing,this.getRsuBeaconData());
        intraUnitAppInteractor.sendV2xMsgToApp(beaconV2xMsg, RsuOFSwitchApp.class);
        adhocBeaconScheduler(); //agenda o próximo envio de beacon

    }

    public void adhocBeaconScheduler(){
        Event event = new Event(getOs().getSimulationTime() + beaconTimeInterval, this,"rsuBeacon");
        getOs().getEventManager().addEvent(event);
    }


    /**
     * Aciona a rotina de envio de Beacons
     **/
    @Override
    public void processEvent(Event event) throws Exception {
        Object resource = event.getResource();
        if(resource instanceof String){
            //strings são recebidas para a construção da mensagem de envio.
            String strResource = (String) resource;
            if(strResource.equals("rsuBeacon")){
                this.adhocBeaconSender();
            }
        }
    }

    @Override
    public void onShutdown() {
        getLog().infoSimTime(this, "Fim de Applicação de envio de Beacons");
    }
}
