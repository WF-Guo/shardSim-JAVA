package edu.pku.infosec.customized.detail;

import edu.pku.infosec.customized.DoNothing;
import edu.pku.infosec.customized.NodeSigningState;
import edu.pku.infosec.event.NodeAction;
import edu.pku.infosec.node.Node;
import edu.pku.infosec.transaction.TxInfo;
import edu.pku.infosec.transaction.TxInput;

import java.util.Set;

import static edu.pku.infosec.customized.ModelData.*;

public class ShardLeaderFinishCoSi implements NodeAction {
    private final TxInfo tx;
    private final CoSiType type;

    public ShardLeaderFinishCoSi(TxInfo tx, CoSiType type) {
        this.tx = tx;
        this.type = type;
    }

    @Override
    public void runOn(Node currentNode) {
        final NodeSigningState state = getState(currentNode.getId(), tx);
        int messageSize = HASH_SIZE/*transaction id*/ + (HASH_SIZE + ECDSA_NUMBER_SIZE)/*CoSi*/ + 1/*type*/
                + (shardLeader2ShardSize.get(currentNode.getId()) - state.acceptCounter) * ECDSA_POINT_SIZE;
        clearState(currentNode.getId(), tx);
        switch (type) {
            case INTRA_SHARD_PREPARE:
                new ShardLeaderStartCoSi(tx, CoSiType.INTRA_SHARD_COMMIT).runOn(currentNode);
                break;
            case INPUT_LOCK_PREPARE:
                new ShardLeaderStartCoSi(tx, CoSiType.INPUT_LOCK_COMMIT).runOn(currentNode);
                break;
            case INPUT_UNLOCK_PREPARE:
                new ShardLeaderStartCoSi(tx, CoSiType.INPUT_UNLOCK_COMMIT).runOn(currentNode);
                break;
            case OUTPUT_PREPARE:
                new ShardLeaderStartCoSi(tx, CoSiType.OUTPUT_COMMIT).runOn(currentNode);
                break;
            case INTRA_SHARD_COMMIT:
                new BroadcastViewInShard(new LocallyCommitTransaction(tx), messageSize).runOn(currentNode);
                break;
            case INPUT_UNLOCK_COMMIT:
                new BroadcastViewInShard(new LocallyUnlockInputs(tx), messageSize).runOn(currentNode);
                break;
            case OUTPUT_COMMIT:
                new BroadcastViewInShard(new LocallyAddOutputs(tx), messageSize).runOn(currentNode);
                break;
            case INPUT_LOCK_COMMIT:
                new BroadcastViewInShard(new LocallyLockInputs(tx), messageSize).runOn(currentNode);
                break;
        }
        switch (type) {
            case INPUT_LOCK_COMMIT:
            case INTRA_SHARD_COMMIT:
            case INPUT_INVALID_PROOF:
            case OUTPUT_COMMIT:
                int shardId = node2Shard.get(currentNode.getId());
                currentNode.sendMessage(ClientAccessPoint.get(tx),
                        new ReturnCoSiToClient(tx, type, shardId, messageSize), messageSize);
        }
    }
}

class LocallyCommitTransaction implements NodeAction {
    private final TxInfo tx;

    public LocallyCommitTransaction(TxInfo tx) {
        this.tx = tx;
    }

    @Override
    public void runOn(Node currentNode) {
        final Set<TxInput> utxoSet = utxoSetOnNode.getGroup(currentNode.getId());
        final Set<TxInput> uncommittedInputs = uncommittedInputsOnNode.getGroup(currentNode.getId());
        tx.inputs.forEach(utxoSet::remove);
        tx.inputs.forEach(uncommittedInputs::remove);
        utxoSet.addAll(tx.outputs);
        currentNode.stayBusy(UTXOSET_OP_TIME * (tx.inputs.size() + tx.outputs.size()), new DoNothing());
    }
}

class LocallyLockInputs implements NodeAction {
    private final TxInfo tx;

    public LocallyLockInputs(TxInfo tx) {
        this.tx = tx;
    }

    @Override
    public void runOn(Node currentNode) {
        int lockNum = 0;
        int shardId = node2Shard.get(currentNode.getId());
        final Set<TxInput> utxoSet = utxoSetOnNode.getGroup(currentNode.getId());
        final Set<TxInput> uncommittedInputs = uncommittedInputsOnNode.getGroup(currentNode.getId());
        for (TxInput input : tx.inputs)
            if (getShardId(input) == shardId) {
                lockNum++;
                utxoSet.remove(input);
                uncommittedInputs.remove(input);
            }
        currentNode.stayBusy(UTXOSET_OP_TIME * lockNum, new DoNothing());
    }
}

class LocallyUnlockInputs implements NodeAction {
    private final TxInfo tx;

    public LocallyUnlockInputs(TxInfo tx) {
        this.tx = tx;
    }

    @Override
    public void runOn(Node currentNode) {
        final Set<TxInput> utxoSet = utxoSetOnNode.getGroup(currentNode.getId());
        int shardId = node2Shard.get(currentNode.getId());
        int lockNum = 0;
        for (TxInput input : tx.inputs)
            if (getShardId(input) == shardId) {
                lockNum++;
                utxoSet.add(input);
            }
        currentNode.stayBusy(UTXOSET_OP_TIME * lockNum, new DoNothing());
    }
}

class LocallyAddOutputs implements NodeAction {
    private final TxInfo tx;

    public LocallyAddOutputs(TxInfo tx) {
        this.tx = tx;
    }

    @Override
    public void runOn(Node currentNode) {
        final Set<TxInput> utxoSet = utxoSetOnNode.getGroup(currentNode.getId());
        int shardId = node2Shard.get(currentNode.getId());
        int addNum = 0;
        for (TxInput output : tx.outputs)
            if (getShardId(output) == shardId) {
                utxoSet.add(output);
                addNum++;
            }
        currentNode.stayBusy(UTXOSET_OP_TIME * addNum, new DoNothing());
    }
}

class ReturnCoSiToClient implements NodeAction {
    private final TxInfo tx;
    private final CoSiType type;
    private final int shardId;
    private final int CoSiSize;

    public ReturnCoSiToClient(TxInfo tx, CoSiType type, int shardId, int coSiSize) {
        this.tx = tx;
        this.type = type;
        this.shardId = shardId;
        CoSiSize = coSiSize;
    }

    @Override
    public void runOn(Node currentNode) {
        currentNode.sendOut(new ClientReceiveCoSi(tx, type, shardId, CoSiSize));
    }
}

