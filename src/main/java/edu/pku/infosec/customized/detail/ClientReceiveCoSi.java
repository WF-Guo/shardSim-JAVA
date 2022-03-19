package edu.pku.infosec.customized.detail;

import edu.pku.infosec.customized.ModelData;
import edu.pku.infosec.event.NodeAction;
import edu.pku.infosec.node.Node;
import edu.pku.infosec.transaction.TxInfo;
import edu.pku.infosec.transaction.TxInput;
import edu.pku.infosec.transaction.TxStat;

import java.util.HashSet;
import java.util.Set;

class ClientReceiveCoSi implements NodeAction {
    private final TxInfo tx;
    private final CoSiType type;
    private final int shardId;

    public ClientReceiveCoSi(TxInfo tx, CoSiType type, int shardId) {
        this.tx = tx;
        this.type = type;
        this.shardId = shardId;
    }

    @Override
    public void runOn(Node currentNode) {
        currentNode.stayBusy(555, new ClientReceiveCoSiImpl(tx, type, shardId));
    }
}

class ClientReceiveCoSiImpl implements NodeAction {
    private final TxInfo tx;
    private final CoSiType type;
    private final int shardId;

    public ClientReceiveCoSiImpl(TxInfo tx, CoSiType type, int shardId) {
        this.tx = tx;
        this.type = type;
        this.shardId = shardId;
    }

    @Override
    public void runOn(Node currentNode) {
        int accessPoint = ModelData.ClientAccessPoint.get(tx);
        switch (type) {
            case INTRA_SHARD_COMMIT:
                TxStat.confirm(tx);
                break;
            case INPUT_LOCK_COMMIT:
                ModelData.ISSet.getGroup(tx).remove(shardId);
                if (ModelData.ISSet.getGroup(tx).isEmpty()) {
                    ModelData.ISSet.removeGroup(tx);
                    if (ModelData.RejectingISs.getGroup(tx).isEmpty())
                        currentNode.sendIn(accessPoint, new GossipToOutputShards(tx));
                    else
                        currentNode.sendIn(accessPoint, new GossipAbortionToInputShards(tx));
                }
                break;
            case INPUT_INVALID_PROOF:
                ModelData.ISSet.getGroup(tx).remove(shardId);
                ModelData.RejectingISs.put(tx, shardId);
                break;
            case OUTPUT_COMMIT:
                ModelData.OSSet.getGroup(tx).remove(shardId);
                if (ModelData.OSSet.getGroup(tx).isEmpty()) {
                    ModelData.OSSet.removeGroup(tx);
                    TxStat.confirm(tx);
                }
                break;
        }
    }
}

class GossipToOutputShards implements NodeAction {
    private final TxInfo tx;

    public GossipToOutputShards(TxInfo tx) {
        this.tx = tx;
    }

    @Override
    public void runOn(Node currentNode) {
        for (Integer shard : ModelData.OSSet.getGroup(tx)) {
            Integer shardLeader = ModelData.shard2Leader.get(shard);
            currentNode.sendMessage(shardLeader, new ShardLeaderStartCoSi(tx, CoSiType.OUTPUT_PREPARE), 555);
        }
    }
}

class GossipAbortionToInputShards implements NodeAction {
    private final TxInfo tx;

    public GossipAbortionToInputShards(TxInfo tx) {
        this.tx = tx;
    }

    @Override
    public void runOn(Node currentNode) {
        Set<Integer> targetShards = new HashSet<>();
        for (TxInput input : tx.inputs)
            targetShards.add(input.hashCode() % ModelData.shardNum);
        targetShards.removeAll(ModelData.RejectingISs.getGroup(tx));
        for (Integer shard : targetShards) {
            Integer shardLeader = ModelData.shard2Leader.get(shard);
            currentNode.sendMessage(shardLeader, new ShardLeaderStartCoSi(tx, CoSiType.INPUT_UNLOCK_PREPARE), 555);
        }
    }
}
