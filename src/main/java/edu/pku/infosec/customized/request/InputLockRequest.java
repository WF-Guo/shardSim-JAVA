package edu.pku.infosec.customized.request;

import edu.pku.infosec.customized.CollectivelySignedMessage;
import edu.pku.infosec.customized.action.ShardLeaderGetRequest;
import edu.pku.infosec.node.Node;
import edu.pku.infosec.transaction.TxInfo;
import edu.pku.infosec.transaction.TxInput;

import java.util.List;

import static edu.pku.infosec.customized.ModelData.*;

public class InputLockRequest extends InputValidationRequest {
    public InputLockRequest(TxInfo tx) {
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
        return tx.inputs.size() * UTXO_REMOVE_TIME;
    }

    @Override
    public void commitInShard(int shardId) {
        for (TxInput input : tx.inputs) {
            if (hashToShard(input) == shardId)
                utxoSet.remove(input);
        }
    }

    @Override
    public void afterCheckingResponse(Node currentNode, CollectivelySignedMessage proof) {
        final List<CollectivelySignedMessage> accept = proofsOfAcceptance.getGroup(tx);
        final List<CollectivelySignedMessage> reject = proofsOfRejection.getGroup(tx);
        accept.add(proof);
        if (accept.size() + reject.size() == ISSet.getGroup(tx).size()) {
            if (reject.isEmpty()) {
                for (Integer OS : OSSet.getGroup(tx)) {
                    int leader = shard2Leader.get(OS);
                    currentNode.sendIn(leader, new ShardLeaderGetRequest(new OutputCommitRequest(tx, accept)));
                }
            } else {
                for (CollectivelySignedMessage signature : accept) {
                    int leader = shard2Leader.get(signature.sourceShard);
                    currentNode.sendIn(leader, new ShardLeaderGetRequest(new InputUnlockRequest(tx, reject.subList(0, 1))));
                }
            }
        }
    }
}
