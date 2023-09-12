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

package org.eclipse.mosaic.app.sdnvfn.message;

import org.eclipse.mosaic.lib.objects.v2x.EncodedPayload;
import org.eclipse.mosaic.lib.objects.v2x.MessageRouting;
import org.eclipse.mosaic.lib.objects.v2x.V2xMessage;

import javax.annotation.Nonnull;
import java.util.HashMap;

/**
 * Class used as message for inter vehicle communication in contrast
 * to the intra vehicle communication.
 */
public final class RsuBeaconV2xMsg extends V2xMessage {

    /**
     * Example payload. The sender puts its geo location
     * inside the message and sends it to every possible receiver.
     */
    private final String beaconStrData;
    private final EncodedPayload payload;
    private final static long minLen = 128L;

    //método construtor da classe
    public RsuBeaconV2xMsg(MessageRouting routing, String beaconStrData) {
        super(routing);
        payload = new EncodedPayload(16L, minLen);
        this.beaconStrData = beaconStrData;
        
    }

    @Nonnull
    @Override
    public EncodedPayload getPayLoad() {
        return payload;
    }

    /* (non-Javadoc)
     * @see org.eclipse.mosaic.lib.objects.v2x.V2xMessage#toString()
     */
    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer("RsuBeaconData{");
        sb.append(beaconStrData);
        sb.append('}');
        return sb.toString();
    }
}
