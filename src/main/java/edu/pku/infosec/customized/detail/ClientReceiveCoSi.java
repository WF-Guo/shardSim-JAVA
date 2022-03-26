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
    private final int CoSiSize;

    public ClientReceiveCoSi(TxInfo tx, CoSiType type, int shardId, int coSiSize) {
        this.tx = tx;
        this.type = type;
        this.shardId = shardId;
        CoSiSize = coSiSize;
    }

    @Override
    public void runOn(Node currentNode) {
        final int txSize = tx.inputs.size() * INPUT_SIZE + tx.outputs.size() * OUTPUT_SIZE + TX_OVERHEAD_SIZE;
        currentNode.stayBusy(
                2 * ECDSA_POINT_MUL_TIME + ECDSA_POINT_ADD_TIME + BYTE_HASH_TIME * (txSize + ECDSA_POINT_SIZE),
                new ClientReceiveCoSiImpl(tx, type, shardId, CoSiSize)
        );
    }
}

class ClientReceiveCoSiImpl implements NodeAction {
    private final TxInfo tx;
    private final CoSiType type;
    private final int shardId;
    private final int CoSiSize;

    public ClientReceiveCoSiImpl(TxInfo tx, CoSiType type, int shardId, int coSiSize) {
        this.tx = tx;
        this.type = type;
        this.shardId = shardId;
        CoSiSize = coSiSize;
    }

    @Override
    public void runOn(Node currentNode) {
        int accessPoint = ClientAccessPoint.get(tx);
        switch (type) {
            case INTRA_SHARD_COMMIT:
                TxStat.confirm(tx);
                break;
            case INPUT_LOCK_COMMIT:
                totalProofSize.add(tx, CoSiSize);
                ISSet.getGroup(tx).remove(shardId);
                if (ISSet.getGroup(tx).isEmpty()) {
                    ISSet.removeGroup(tx);
                    if (RejectingISs.getGroup(tx).isEmpty())
                        currentNode.sendIn(accessPoint, new GossipToOutputShards(tx, totalProofSize.count(tx)));
                    else
                        currentNode.sendIn(accessPoint, new GossipAbortionToInputShards(tx, rejectProofSize.get(tx)));
                    rejectProofSize.remove(tx);
                    totalProofSize.clear(tx);
                }
                break;
            case INPUT_INVALID_PROOF:
                ISSet.getGroup(tx).remove(shardId);
                RejectingISs.put(tx, shardId);
                rejectProofSize.put(tx, CoSiSize);
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
    private final int CoSiSize;

    public GossipToOutputShards(TxInfo tx, int coSiSize) {
        this.tx = tx;
        CoSiSize = coSiSize;
    }

    @Override
    public void runOn(Node currentNode) {
        for (Integer shard : OSSet.getGroup(tx)) {
            Integer shardLeader = shard2Leader.get(shard);
            currentNode.sendMessage(shardLeader, new ShardLeaderStartCoSi(tx, CoSiType.OUTPUT_PREPARE), CoSiSize);
        }
    }
}

class GossipAbortionToInputShards implements NodeAction {
    private final TxInfo tx;
    private final int CoSiSize;

    public GossipAbortionToInputShards(TxInfo tx, int coSiSize) {
        this.tx = tx;
        CoSiSize = coSiSize;
    }

    @Override
    public void runOn(Node currentNode) {
        Set<Integer> targetShards = new HashSet<>();
        for (TxInput input : tx.inputs)
            targetShards.add(getShardId(input));
        targetShards.removeAll(RejectingISs.getGroup(tx));
        for (Integer shard : targetShards) {
            Integer shardLeader = shard2Leader.get(shard);
            currentNode.sendMessage(shardLeader, new ShardLeaderStartCoSi(tx, CoSiType.INPUT_UNLOCK_PREPARE), CoSiSize);
        }
    }
}
