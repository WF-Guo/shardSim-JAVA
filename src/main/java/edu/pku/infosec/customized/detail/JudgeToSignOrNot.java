package edu.pku.infosec.customized.detail;

import edu.pku.infosec.customized.NodeSigningState;
import edu.pku.infosec.event.NodeAction;
import edu.pku.infosec.node.Node;
import edu.pku.infosec.transaction.TxInfo;
import edu.pku.infosec.transaction.TxInput;

import java.util.Set;

import static edu.pku.infosec.customized.ModelData.*;

public class JudgeToSignOrNot implements NodeAction {
    private final TxInfo tx;
    private final CoSiType type;
    private final NodeAction actionWhenYes, actionWhenNo;

    public JudgeToSignOrNot(TxInfo tx, CoSiType type, NodeAction actionWhenYes, NodeAction actionWhenNo) {
        this.tx = tx;
        this.type = type;
        this.actionWhenYes = actionWhenYes;
        this.actionWhenNo = actionWhenNo;
    }

    @Override
    public void runOn(Node currentNode) {
        int shardId = node2Shard.get(currentNode.getId());
        final NodeSigningState state = getState(currentNode.getId(), tx);
        final Set<TxInput> utxoSet = utxoSetOnNode.getGroup(currentNode.getId());
        final Set<TxInput> uncommittedInputs = uncommittedInputsOnNode.getGroup(currentNode.getId());
        final int txSize = tx.inputs.size() * INPUT_SIZE + tx.outputs.size() * OUTPUT_SIZE + TX_OVERHEAD_SIZE;
        double timeCost = 0;
        switch (type) {
            case INTRA_SHARD_PREPARE:
            case INPUT_LOCK_PREPARE:
                state.admitted = true;
                if (maliciousNodes.contains(currentNode.getId()))
                    break;
                for (TxInput input : tx.inputs) {
                    if (getShardId(input) == shardId) {
                        timeCost += UTXOSET_OP_TIME +
                                BYTE_HASH_TIME * (txSize + ECDSA_POINT_SIZE) + // c=hash(m|R)
                                2 * ECDSA_POINT_MUL_TIME + ECDSA_POINT_ADD_TIME; // cX + sG
                        if (!utxoSet.contains(input) || uncommittedInputs.contains(input)) {
                            state.admitted = false;
                            break;
                        }
                    }
                }
                for (TxInput input : tx.inputs) {
                    if (getShardId(input) == shardId) {
                        uncommittedInputs.add(input);
                    }
                }
                break;
            case INPUT_INVALID_PROOF:
                state.admitted = false;
                if (maliciousNodes.contains(currentNode.getId()))
                    break;
                for (TxInput input : tx.inputs) {
                    if (getShardId(input) == shardId) {
                        timeCost += UTXOSET_OP_TIME;
                        if(!utxoSet.contains(input)) {
                            state.admitted = true;
                            break;
                        }
                    }
                }
                break;
            case INPUT_UNLOCK_PREPARE:
            case OUTPUT_PREPARE:
            case INTRA_SHARD_COMMIT:
            case INPUT_LOCK_COMMIT:
            case INPUT_UNLOCK_COMMIT:
            case OUTPUT_COMMIT:
                state.admitted = true; // Because client never sends fake proof
                break;
        }
        if (state.admitted)
            currentNode.stayBusy(timeCost, actionWhenYes);
        else
            currentNode.stayBusy(timeCost, actionWhenNo);
    }
}
