package edu.pku.infosec.customized.request;

import edu.pku.infosec.customized.CollectivelySignedMessage;
import edu.pku.infosec.customized.VerificationResult;
import edu.pku.infosec.node.Node;
import edu.pku.infosec.transaction.TxInfo;

import java.util.List;

import static edu.pku.infosec.customized.ModelData.*;

public abstract class CoSiValidationRequest extends Request {
    public final int size;
    public final List<CollectivelySignedMessage> signatures;

    public CoSiValidationRequest(TxInfo tx, List<CollectivelySignedMessage> signatures) {
        super(tx);
        this.signatures = signatures;
        int sizeSum = tx.inputs.size() * INPUT_SIZE + tx.outputs.size() * OUTPUT_SIZE + TX_OVERHEAD_SIZE + 1;
        for (CollectivelySignedMessage signature : signatures) {
            sizeSum += signature.getSize();
        }
        size = sizeSum;
    }

    @Override
    public int getSize() {
        return size;
    }

    @Override
    public VerificationResult verifyOn(Node currentNode) {
        double timeSum = 0;
        for (CollectivelySignedMessage signature : signatures) {
            timeSum += signature.verifyOn(currentNode).timeCost;
        }
        return new VerificationResult(timeSum, true);
    }
}
