package edu.pku.infosec.customized.request;

import edu.pku.infosec.customized.CollectivelySignedMessage;
import edu.pku.infosec.customized.VerificationResult;
import edu.pku.infosec.node.Node;
import edu.pku.infosec.transaction.TxInfo;

public abstract class Request {
    public final TxInfo tx;

    public Request(TxInfo tx) {
        this.tx = tx;
    }

    abstract public int getSize();

    abstract public VerificationResult verifyOn(Node currentNode);

    abstract public double commitTime(Node currentNode);

    abstract public void commitInShard(int shardId);

    abstract public void afterCheckingResponse(Node currentNode, CollectivelySignedMessage proof);
}
