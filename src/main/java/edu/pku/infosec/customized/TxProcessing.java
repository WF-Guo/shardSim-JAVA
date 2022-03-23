package edu.pku.infosec.customized;

import edu.pku.infosec.customized.detail.CoSiType;
import edu.pku.infosec.customized.detail.ShardLeaderStartCoSi;
import edu.pku.infosec.event.NodeAction;
import edu.pku.infosec.node.Node;
import edu.pku.infosec.transaction.TxInfo;
import edu.pku.infosec.transaction.TxInput;

import java.util.HashSet;
import java.util.Set;

import static edu.pku.infosec.customized.ModelData.*;

public class TxProcessing implements NodeAction {
    private final TxInfo txInfo;

    public TxProcessing(TxInfo txInfo) {
        this.txInfo = txInfo;
    }

    @Override
    public void runOn(Node currentNode) {
        ClientAccessPoint.put(txInfo, currentNode.getId());
        Set<Integer> shardSet = new HashSet<>();
        for (TxInput input : txInfo.inputs) {
            ISSet.put(txInfo, getShardId(input));
        }
        for (TxInput output : txInfo.outputs) {
            OSSet.put(txInfo, getShardId(output));
        }
        shardSet.addAll(ISSet.getGroup(txInfo));
        shardSet.addAll(OSSet.getGroup(txInfo));
        for (Integer shard : ISSet.getGroup(txInfo)) {
            Integer shardLeader = shard2Leader.get(shard);
            if (shardSet.size() == 1)
                currentNode.sendMessage(shardLeader, new ShardLeaderStartCoSi(txInfo, CoSiType.INTRA_SHARD_PREPARE), 555);
            else
                currentNode.sendMessage(shardLeader, new ShardLeaderStartCoSi(txInfo, CoSiType.INPUT_LOCK_PREPARE), 555);
        }
    }
}
