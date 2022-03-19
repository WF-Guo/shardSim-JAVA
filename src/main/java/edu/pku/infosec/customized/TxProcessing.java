package edu.pku.infosec.customized;

import edu.pku.infosec.customized.detail.CoSiType;
import edu.pku.infosec.customized.detail.ShardLeaderStartCoSi;
import edu.pku.infosec.event.NodeAction;
import edu.pku.infosec.node.Node;
import edu.pku.infosec.transaction.TxInfo;
import edu.pku.infosec.transaction.TxInput;

import java.util.HashSet;
import java.util.Set;

public class TxProcessing implements NodeAction {
    private final TxInfo txInfo;

    public TxProcessing(TxInfo txInfo) {
        this.txInfo = txInfo;
    }

    @Override
    public void runOn(Node currentNode) {
        ModelData.ClientAccessPoint.put(txInfo, currentNode.getId());
        Set<Integer> shardSet = new HashSet<>();
        for (TxInput input : txInfo.inputs) {
            ModelData.ISSet.put(txInfo, input.hashCode() % ModelData.shardNum);
        }
        for (TxInput output: txInfo.outputs) {
            ModelData.OSSet.put(txInfo, output.hashCode() % ModelData.shardNum);
        }
        shardSet.addAll(ModelData.ISSet.getGroup(txInfo));
        shardSet.addAll(ModelData.OSSet.getGroup(txInfo));
        for (Integer shard : ModelData.ISSet.getGroup(txInfo)) {
            Integer shardLeader = ModelData.shard2Leader.get(shard);
            if (shardSet.size() == 1)
                currentNode.sendMessage(shardLeader, new ShardLeaderStartCoSi(txInfo, CoSiType.INTRA_SHARD_PREPARE), 555);
            else
                currentNode.sendMessage(shardLeader, new ShardLeaderStartCoSi(txInfo, CoSiType.INPUT_LOCK_PREPARE), 555);
        }
    }
}
