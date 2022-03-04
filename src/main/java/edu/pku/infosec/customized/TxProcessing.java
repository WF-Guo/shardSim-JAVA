package edu.pku.infosec.customized;

import edu.pku.infosec.event.NodeAction;
import edu.pku.infosec.node.Node;
import edu.pku.infosec.transaction.TxInfo;

public class TxProcessing implements NodeAction {
    private final TxInfo txInfo;

    public TxProcessing(TxInfo txInfo) {
        this.txInfo = txInfo;
    }

    @Override
    public void runOn(Node currentNode) {
        /*
            Define your transaction processing rule here
         */
    }
}
