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
public class VehicleConfig {
    public Float radioRange;
    public Integer defautMsgHops;
    public ArrayList<String> vehicleServiceList = new ArrayList<>();
    public String rsuAccessPointId;
    public String rsuNet;
    public String serviceResultMsgType;
    public String vfnServiceMsgType;
    public Float maxHeadingDifference;
    public Float headingDiferenceWeigth;
    public Float maxHeadingDifferenceList1;
    public Float maxHeadingDifferenceList2;
    public int maxRsuListSize;
    public Float fixedVirtualSpeed;

}
