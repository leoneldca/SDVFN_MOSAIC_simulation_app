package org.eclipse.mosaic.app.sdnvfn.network;

import org.eclipse.mosaic.app.sdnvfn.RsuKnowledgeBaseApp;
import org.eclipse.mosaic.app.sdnvfn.config.RsuOFSwitchAppConfig;
import org.eclipse.mosaic.app.sdnvfn.message.GenericV2xMessage;
import org.eclipse.mosaic.app.sdnvfn.utils.IntraUnitAppInteractor;
import org.eclipse.mosaic.fed.application.app.api.OperatingSystemAccess;
import org.eclipse.mosaic.fed.application.app.api.os.OperatingSystem;
import org.eclipse.mosaic.lib.enums.AdHocChannel;

public class OFSwitchPorts {
    private CommunicationInterface communicationInterface;
    private IntraUnitAppInteractor intraUnitAppInteractor;
    private RsuOFSwitchAppConfig rsuOFSwitchAppConfig;
    private final int INTRAUNIT_PORT = 1;
    private final int ADHOC_PORT = 2;
    private final int SERVER_PORT = 3;
    private final int RSUS_PORT = 4;


    public OFSwitchPorts(OperatingSystemAccess<? extends OperatingSystem> unitOsAccess, RsuOFSwitchAppConfig switchConf) {
        rsuOFSwitchAppConfig = switchConf;
        //deve receber o acesso à OBU local
        this.communicationInterface = new CommunicationInterface(unitOsAccess);
        this.communicationInterface.createAdHocInterface(this.rsuOFSwitchAppConfig.radioRange,AdHocChannel.CCH);
        this.communicationInterface.createCellInterface();

        //instanciar IntraAppInteractor
        intraUnitAppInteractor = new IntraUnitAppInteractor(unitOsAccess);

        //receber a string de conexões RSU e proceder com o mapeamento de cada RSU em uma porta


    }
    public int getIntraUnitPort() {
        return INTRAUNIT_PORT;
    }
    public int getAdhocPort(){
        return ADHOC_PORT;
    }
    public int getServerPort() {
        return SERVER_PORT;
    }
    public int getRsusPort() {
        return RSUS_PORT;
    }





    public void sendMessage(int port, GenericV2xMessage v2xMessage) {
        if(port<1 || port>20){
            System.out.println("Número da porta deve estar entre 1 e 20 inclusive");
            return;
            //limitado a 20 portas
        }

       switch (port) {
           case INTRAUNIT_PORT:
                //reservada para intraUnit Comunication: Enviar para execução local RsuKnowlageBaseApp via event process
                intraUnitAppInteractor.sendV2xMsgToApp(v2xMessage, RsuKnowledgeBaseApp.class);
                break;

           case ADHOC_PORT:
                //reservado para comunicação pela rede AdHoc / veículos
                communicationInterface.sendAdhocV2xMessage(v2xMessage);
                break;


           default:
                //enviar mensagem pela rede celular: Comunicações para outras RSUs e para Controladores
                communicationInterface.sendCellV2xMessage(v2xMessage);
                break;

        }
    }


}
