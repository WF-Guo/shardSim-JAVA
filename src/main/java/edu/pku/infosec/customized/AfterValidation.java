package edu.pku.infosec.customized;

import edu.pku.infosec.event.EventDriver;
import edu.pku.infosec.event.EventHandler;
import edu.pku.infosec.event.EventParam;
import edu.pku.infosec.node.Node;
import edu.pku.infosec.transaction.TxInfo;
import edu.pku.infosec.transaction.TxInput;
import edu.pku.infosec.transaction.TxStat;

public class AfterValidation implements EventHandler {
    @Override
    public void run(Node currentNode, EventParam param) {
        TxInfo txInfo = (TxInfo) param;
        System.out.println(txInfo.id + " has been received at " + EventDriver.getCurrentTime());
        System.out.println(txInfo.inputs);
        boolean ok = true;
        for(TxInput txInput: txInfo.inputs) {
            if (!ModelData.utxoSet.contains(txInput)) {
                ok = false;
                break;
            }
        }
        if(ok) {
            TxStat.commit(txInfo);
            for(TxInput txInput: txInfo.inputs)
                ModelData.utxoSet.remove(txInput);
        }
    }
}
