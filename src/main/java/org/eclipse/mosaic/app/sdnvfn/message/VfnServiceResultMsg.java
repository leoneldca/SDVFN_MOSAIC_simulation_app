package org.eclipse.mosaic.app.sdnvfn.message;

import org.eclipse.mosaic.lib.objects.v2x.MessageRouting;

public class VfnServiceResultMsg extends GenericV2xMessage{

    public VfnServiceResultMsg(MessageRouting routing, String msgStrData) {
        super(routing, msgStrData);
    }
}
