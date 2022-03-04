package edu.pku.infosec.customized;

import edu.pku.infosec.event.EventDriver;
import edu.pku.infosec.event.NodeAction;
import edu.pku.infosec.node.Node;
import edu.pku.infosec.transaction.TxInfo;
import edu.pku.infosec.transaction.TxInput;
import edu.pku.infosec.transaction.TxStat;

public class AfterValidation implements NodeAction {
    private final TxInfo txInfo;

    public AfterValidation(TxInfo txInfo) {
        this.txInfo = txInfo;
    }

    @Override
    public void runOn(Node currentNode) {
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
            TxStat.confirm(txInfo);
            for(TxInput txInput: txInfo.inputs)
                ModelData.utxoSet.remove(txInput);
        }
    }
}
