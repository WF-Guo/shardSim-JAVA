package edu.pku.infosec.customized.request;

import edu.pku.infosec.customized.CollectivelySignedMessage;
import edu.pku.infosec.customized.ModelData;
import edu.pku.infosec.node.Node;
import edu.pku.infosec.transaction.TxInfo;
import edu.pku.infosec.transaction.TxInput;

import java.util.List;

import static edu.pku.infosec.customized.ModelData.*;

public class InputUnlockRequest extends CoSiValidationRequest {
    public InputUnlockRequest(TxInfo tx, List<CollectivelySignedMessage> signatures) {
        super(tx, signatures);
    }

    @Override
    public double commitTime(Node currentNode) {
        return tx.inputs.size() * ModelData.UTXO_INSERT_TIME;
    }

    @Override
    public void commitInShard(int shardId) {
        for (TxInput input : tx.inputs) {
            if (hashToShard(input) == shardId)
                utxoSet.add(input);
        }
    }


    @Override
    public void afterCheckingResponse(Node currentNode, CollectivelySignedMessage proof) {
        CollectivelySignedMessage brokenLock = null;
        final List<CollectivelySignedMessage> locks = proofsOfAcceptance.getGroup(tx);
        for (CollectivelySignedMessage lock : locks) {
            if (lock.sourceShard == proof.sourceShard) {
                brokenLock = lock;
                break;
            }
        }
        locks.remove(brokenLock);
        if (locks.isEmpty())
            clearClientState(tx);
    }
}
