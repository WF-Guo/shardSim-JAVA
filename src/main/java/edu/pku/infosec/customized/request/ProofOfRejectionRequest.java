package edu.pku.infosec.customized.request;

import edu.pku.infosec.customized.CollectivelySignedMessage;
import edu.pku.infosec.customized.VerificationResult;
import edu.pku.infosec.customized.action.ShardLeaderGetRequest;
import edu.pku.infosec.node.Node;
import edu.pku.infosec.transaction.TxInfo;
import edu.pku.infosec.transaction.TxInput;

import java.util.List;
import java.util.Set;

import static edu.pku.infosec.customized.ModelData.*;

public class ProofOfRejectionRequest extends Request {

    public final int size;

    public ProofOfRejectionRequest(TxInfo tx) {
        super(tx);
        size = tx.inputs.size() * INPUT_SIZE + tx.outputs.size() * OUTPUT_SIZE + TX_OVERHEAD_SIZE + 1/*type*/;
    }

    @Override
    public int getSize() {
        return size;
    }

    @Override
    public VerificationResult verifyOn(Node currentNode) {
        final int shardId = node2Shard.get(currentNode.getId());
        final Set<TxInput> spending = node2SpendingSet.getGroup(currentNode);
        double timeCost = 0;
        for (TxInput input : tx.inputs) {
            if (hashToShard(input) == shardId) {
                timeCost += UTXO_SELECT_TIME;
                if (!utxoSet.contains(input) || spending.contains(input)) {
                    return new VerificationResult(timeCost, true);
                }
            }
        }
        return new VerificationResult(timeCost, false);
    }

    @Override
    public double commitTime(Node currentNode) {
        return 0;
    }

    @Override
    public void commitInShard(int shardId) {
    }

    @Override
    public void afterCheckingResponse(Node currentNode, CollectivelySignedMessage proof) {
        final List<CollectivelySignedMessage> accept = proofsOfAcceptance.getGroup(tx);
        final List<CollectivelySignedMessage> reject = proofsOfRejection.getGroup(tx);
        reject.add(proof);
        if (accept.size() + reject.size() == ISSet.getGroup(tx).size()) {
            for (CollectivelySignedMessage signature : accept) {
                int leader = shard2Leader.get(signature.sourceShard);
                currentNode.sendIn(leader, new ShardLeaderGetRequest(new InputUnlockRequest(tx, reject.subList(0, 1))));
            }
        }
    }
}
