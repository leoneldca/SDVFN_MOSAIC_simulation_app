/*
 * Copyright (c) 2023 Leonel Diógenes Carvalhaes Alvarenga. All rights reserved.
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
 * Contact: leonel.carvalhaes@ifgoiano.edu.br
 */

package org.eclipse.mosaic.app.sdnvfn.config;


import java.net.Inet4Address;
import java.util.ArrayList;

/**
 * This is the configuration class for RSUs.
 */
public class RsuOFSwitchAppConfig {
    public Float radioRange;
    public Integer defautMsgHops;
    public String serverAddress;
    public String serverName;
    public String netMask;
    public String vehicleNet;
    public String rsuNet;
    public String tlNet;
    public String csNet;
    public String serverNet;
    public String tmcNet;
    public String serviceResultMsgType;
    public String vfnServiceMsgType;
    public String openFlowMsgType;
    public String openFlowPacketInMsg;
    public String openFlowPacketOutMsg;
    public String openFlowFlowMod;
    public ArrayList<String> rsusConnections;
}
