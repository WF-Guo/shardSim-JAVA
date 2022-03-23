package edu.pku.infosec.customized.detail;

import edu.pku.infosec.event.NodeAction;
import edu.pku.infosec.node.Node;
import edu.pku.infosec.transaction.TxInfo;
import edu.pku.infosec.transaction.TxInput;
import edu.pku.infosec.transaction.TxStat;

import java.util.HashSet;
import java.util.Set;

import static edu.pku.infosec.customized.ModelData.*;

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
        int accessPoint = ClientAccessPoint.get(tx);
        switch (type) {
            case INTRA_SHARD_COMMIT:
                TxStat.confirm(tx);
                break;
            case INPUT_LOCK_COMMIT:
                ISSet.getGroup(tx).remove(shardId);
                if (ISSet.getGroup(tx).isEmpty()) {
                    ISSet.removeGroup(tx);
                    if (RejectingISs.getGroup(tx).isEmpty())
                        currentNode.sendIn(accessPoint, new GossipToOutputShards(tx));
                    else
                        currentNode.sendIn(accessPoint, new GossipAbortionToInputShards(tx));
                }
                break;
            case INPUT_INVALID_PROOF:
                ISSet.getGroup(tx).remove(shardId);
                RejectingISs.put(tx, shardId);
                break;
            case OUTPUT_COMMIT:
                OSSet.getGroup(tx).remove(shardId);
                if (OSSet.getGroup(tx).isEmpty()) {
                    OSSet.removeGroup(tx);
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
        for (Integer shard : OSSet.getGroup(tx)) {
            Integer shardLeader = shard2Leader.get(shard);
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
            targetShards.add(getShardId(input));
        targetShards.removeAll(RejectingISs.getGroup(tx));
        for (Integer shard : targetShards) {
            Integer shardLeader = shard2Leader.get(shard);
            currentNode.sendMessage(shardLeader, new ShardLeaderStartCoSi(tx, CoSiType.INPUT_UNLOCK_PREPARE), 555);
        }
    }
}
