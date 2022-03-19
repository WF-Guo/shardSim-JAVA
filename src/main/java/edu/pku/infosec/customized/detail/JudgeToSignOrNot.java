package edu.pku.infosec.customized.detail;

import edu.pku.infosec.customized.ModelData;
import edu.pku.infosec.customized.NodeSigningState;
import edu.pku.infosec.event.NodeAction;
import edu.pku.infosec.node.Node;
import edu.pku.infosec.transaction.TxInfo;
import edu.pku.infosec.transaction.TxInput;

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
        int shardId = ModelData.node2Shard.get(currentNode.getId());
        final NodeSigningState state = ModelData.getState(currentNode.getId(), tx);
        switch (type) {
            case INTRA_SHARD_PREPARE:
            case INPUT_LOCK_PREPARE:
                state.admitted = true;
                if (ModelData.maliciousNodes.contains(currentNode.getId()))
                    break;
                for (TxInput input : tx.inputs) {
                    if (input.hashCode() % ModelData.shardNum == shardId &&
                            !ModelData.utxoSetOfNode(currentNode.getId()).contains(input)) {
                        state.admitted = false;
                        break;
                    }
                }
                break;
            case INPUT_INVALID_PROOF:
                state.admitted = false;
                if (ModelData.maliciousNodes.contains(currentNode.getId()))
                    break;
                for (TxInput input : tx.inputs) {
                    if (input.hashCode() % ModelData.shardNum == shardId &&
                            !ModelData.utxoSetOfNode(currentNode.getId()).contains(input)) {
                        state.admitted = true;
                        break;
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
            actionWhenYes.runOn(currentNode);
        else
            actionWhenNo.runOn(currentNode);
    }
}
