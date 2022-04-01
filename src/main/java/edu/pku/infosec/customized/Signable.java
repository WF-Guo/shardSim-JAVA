package edu.pku.infosec.customized;

import edu.pku.infosec.event.NodeAction;
import edu.pku.infosec.node.Node;

public interface Signable {
    VerificationResult verifyOn(Node currentNode);

    NodeAction actionAfterSigning(int absentNum, int shardId);

    int getSize();
}
