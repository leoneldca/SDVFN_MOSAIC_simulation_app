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

package org.eclipse.mosaic.app.sdnvfn.config;

import java.util.ArrayList;

/**
 * This is an example of a simple configuration class.
 */
public class ServerConfig {
    public Float adHocRadioRange;
    public Float handoverZoneMultiplier;
    public Float handoverPredictionZoneMultiplier;
    public ArrayList<String> commonServiceList = new ArrayList<>();
    public ArrayList<String> specificServiceList = new ArrayList<>();
    public String rsuInitialRunner;
    public String beaconMsgType;
    public String carBeaconType;
    public String vfnServiceMsgType;
    public String rsuRunnerMsgType;
    public String openFlowMsgType;
    public String openFlowPacketInMsg;
    public String openFlowPacketOutMsg;
    public String openFlowFlowMod;

    public Float headingDiferenceWeigth;
    public Float maxHeadingDifferenceList1;
    public Float maxHeadingDifferenceList2;
    public int maxRsuListSize;
    public Float fixedVirtualSpeed;

    public String serverAddress;
    public String serverName;
    public String netMask;
    public String vehicleNet;
    public String rsuNet;
    public String tlNet;
    public String csNet;
    public String serverNet;
    public String tmcNet;
    public ArrayList<String> rsusConnections;
    public ArrayList<String> rsusPositions;
    public String fcnDistributionType;
    public ArrayList<String> fcnList;

}
