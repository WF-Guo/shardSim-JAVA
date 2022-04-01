package edu.pku.infosec.customized.request;

import edu.pku.infosec.customized.CollectivelySignedMessage;
import edu.pku.infosec.node.Node;
import edu.pku.infosec.transaction.TxInfo;
import edu.pku.infosec.transaction.TxInput;
import edu.pku.infosec.transaction.TxStat;

import java.util.List;

import static edu.pku.infosec.customized.ModelData.*;

public class OutputCommitRequest extends CoSiValidationRequest {
    public OutputCommitRequest(TxInfo tx, List<CollectivelySignedMessage> signatures) {
        super(tx, signatures);
    }

    @Override
    public double commitTime(Node currentNode) {
        return tx.outputs.size() * UTXO_INSERT_TIME;
    }


    @Override
    public void commitInShard(int shardId) {
        for (TxInput output : tx.outputs) {
            if (hashToShard(output) == shardId)
                utxoSet.add(output);
        }
    }


    @Override
    public void afterCheckingResponse(Node currentNode, CollectivelySignedMessage proof) {
        commitCounter.add(tx);
        if (commitCounter.count(tx) == OSSet.getGroup(tx).size()) {
            TxStat.confirm(tx);
            clearClientState(tx);
        }
    }
}
