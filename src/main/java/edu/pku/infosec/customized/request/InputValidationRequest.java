package edu.pku.infosec.customized.request;

import edu.pku.infosec.customized.VerificationResult;
import edu.pku.infosec.node.Node;
import edu.pku.infosec.transaction.TxInfo;
import edu.pku.infosec.transaction.TxInput;

import java.util.Set;

import static edu.pku.infosec.customized.ModelData.*;

public abstract class InputValidationRequest extends Request {
    public final int size;

    public InputValidationRequest(TxInfo tx) {
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
                timeCost += UTXO_SELECT_TIME +
                        BYTE_HASH_TIME * (size + ECDSA_POINT_SIZE) + // c=hash(m|R)
                        2 * ECDSA_POINT_MUL_TIME + ECDSA_POINT_ADD_TIME; // cX + sG
                if (!utxoSet.contains(input) || spending.contains(input)) {
                    return new VerificationResult(timeCost, false);
                }
            }
        }
        for (TxInput input : tx.inputs) {
            if (hashToShard(input) == shardId) {
                spending.add(input);
            }
        }
        return new VerificationResult(timeCost, true);
    }
}
