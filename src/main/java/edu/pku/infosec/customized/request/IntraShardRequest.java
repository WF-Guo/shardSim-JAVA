package edu.pku.infosec.customized.request;

import edu.pku.infosec.customized.CollectivelySignedMessage;
import edu.pku.infosec.node.Node;
import edu.pku.infosec.transaction.TxInfo;
import edu.pku.infosec.transaction.TxInput;
import edu.pku.infosec.transaction.TxStat;

import static edu.pku.infosec.customized.ModelData.*;

public class IntraShardRequest extends InputValidationRequest {
    public IntraShardRequest(TxInfo tx) {
        super(tx);
    }

    @Override
    public double commitTime(Node currentNode) {
        int shardId = node2Shard.get(currentNode.getId());
        for (TxInput input : tx.inputs) {
            if (hashToShard(input) == shardId) {
                node2SpendingSet.getGroup(currentNode).remove(input);
            }
        }
        return UTXO_REMOVE_TIME * tx.inputs.size() + UTXO_INSERT_TIME * tx.outputs.size();
    }

    @Override
    public void commitInShard(int shardId) {
        for (TxInput input : tx.inputs) {
            if (hashToShard(input) == shardId)
                utxoSet.remove(input);
        }
        for (TxInput output : tx.outputs) {
            if (hashToShard(output) == shardId)
                utxoSet.add(output);
        }
    }

    @Override
    public void afterCheckingResponse(Node currentNode, CollectivelySignedMessage proof) {
        TxStat.confirm(tx);
        clearClientState(tx);
    }
}
