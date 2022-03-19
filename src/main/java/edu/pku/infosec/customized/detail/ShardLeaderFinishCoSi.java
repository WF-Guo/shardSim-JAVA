package edu.pku.infosec.customized.detail;

import edu.pku.infosec.customized.ModelData;
import edu.pku.infosec.event.NodeAction;
import edu.pku.infosec.node.Node;
import edu.pku.infosec.transaction.TxInfo;
import edu.pku.infosec.transaction.TxInput;

import java.util.Set;

public class ShardLeaderFinishCoSi implements NodeAction {
    private final TxInfo tx;
    private final CoSiType type;

    public ShardLeaderFinishCoSi(TxInfo tx, CoSiType type) {
        this.tx = tx;
        this.type = type;
    }

    @Override
    public void runOn(Node currentNode) {
        ModelData.clearState(currentNode.getId(), tx);
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
                new BroadcastViewInShard(new LocallyCommitTransaction(tx), 555).runOn(currentNode);
                break;
            case INPUT_UNLOCK_COMMIT:
                new BroadcastViewInShard(new LocallyUnlockInputs(tx), 555).runOn(currentNode);
                break;
            case OUTPUT_COMMIT:
                new BroadcastViewInShard(new LocallyAddOutputs(tx), 555).runOn(currentNode);
                break;
            case INPUT_LOCK_COMMIT:
                new BroadcastViewInShard(new LocallyLockInputs(tx), 555).runOn(currentNode);
                break;
        }
        switch (type) {
            case INPUT_LOCK_COMMIT:
            case INTRA_SHARD_COMMIT:
            case INPUT_INVALID_PROOF:
            case OUTPUT_COMMIT:
                int shardId = ModelData.node2Shard.get(currentNode.getId());
                currentNode.sendMessage(ModelData.ClientAccessPoint.get(tx),
                        new ReturnCoSiToClient(tx, type, shardId), 555);
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
        currentNode.stayBusy(555 * (tx.inputs.size() + tx.outputs.size()),
                new LocallyCommitTransactionImpl(tx));
    }
}

class LocallyCommitTransactionImpl implements NodeAction {
    private final TxInfo tx;

    public LocallyCommitTransactionImpl(TxInfo tx) {
        this.tx = tx;
    }

    @Override
    public void runOn(Node currentNode) {
        final Set<TxInput> utxoSet = ModelData.utxoSetOfNode(currentNode.getId());
        tx.inputs.forEach(utxoSet::remove);
        utxoSet.addAll(tx.outputs);
    }
}

class LocallyLockInputs implements NodeAction {
    private final TxInfo tx;

    public LocallyLockInputs(TxInfo tx) {
        this.tx = tx;
    }

    @Override
    public void runOn(Node currentNode) {
        currentNode.stayBusy(555 * tx.inputs.size(), new LocallyLockInputsImpl(tx));
    }
}

class LocallyLockInputsImpl implements NodeAction {
    private final TxInfo tx;

    public LocallyLockInputsImpl(TxInfo tx) {
        this.tx = tx;
    }

    @Override
    public void runOn(Node currentNode) {
        final Set<TxInput> utxoSet = ModelData.utxoSetOfNode(currentNode.getId());
        tx.inputs.forEach(utxoSet::remove); // Only those in set will be removed
    }
}

class LocallyUnlockInputs implements NodeAction {
    private final TxInfo tx;

    public LocallyUnlockInputs(TxInfo tx) {
        this.tx = tx;
    }

    @Override
    public void runOn(Node currentNode) {
        currentNode.stayBusy(555 * tx.inputs.size(), new LocallyUnlockInputsImpl(tx));
    }
}

class LocallyUnlockInputsImpl implements NodeAction {
    private final TxInfo tx;

    public LocallyUnlockInputsImpl(TxInfo tx) {
        this.tx = tx;
    }

    @Override
    public void runOn(Node currentNode) {
        final Set<TxInput> utxoSet = ModelData.utxoSetOfNode(currentNode.getId());
        int shardId = ModelData.node2Shard.get(currentNode.getId());
        for (TxInput input : tx.inputs)
            if (input.hashCode() % ModelData.shardNum == shardId)
                utxoSet.add(input);
    }
}

class LocallyAddOutputs implements NodeAction {
    private final TxInfo tx;

    public LocallyAddOutputs(TxInfo tx) {
        this.tx = tx;
    }

    @Override
    public void runOn(Node currentNode) {
        currentNode.stayBusy(555 * tx.outputs.size(), new LocallyAddOutputsImpl(tx));
    }
}

class LocallyAddOutputsImpl implements NodeAction {
    private final TxInfo tx;

    public LocallyAddOutputsImpl(TxInfo tx) {
        this.tx = tx;
    }

    @Override
    public void runOn(Node currentNode) {
        final Set<TxInput> utxoSet = ModelData.utxoSetOfNode(currentNode.getId());
        int shardId = ModelData.node2Shard.get(currentNode.getId());
        for (TxInput output : tx.outputs)
            if (output.hashCode() % ModelData.shardNum == shardId)
                utxoSet.add(output);
    }
}

class ReturnCoSiToClient implements NodeAction {
    private final TxInfo tx;
    private final CoSiType type;
    private final int shardId;

    public ReturnCoSiToClient(TxInfo tx, CoSiType type, int shardId) {
        this.tx = tx;
        this.type = type;
        this.shardId = shardId;
    }

    @Override
    public void runOn(Node currentNode) {
        currentNode.sendOut(new ClientReceiveCoSi(tx, type, shardId));
    }
}

