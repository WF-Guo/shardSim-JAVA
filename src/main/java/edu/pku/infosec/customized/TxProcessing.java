package edu.pku.infosec.customized;

import edu.pku.infosec.customized.action.ShardLeaderGetRequest;
import edu.pku.infosec.customized.request.InputLockRequest;
import edu.pku.infosec.customized.request.IntraShardRequest;
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
        int txSize = txInfo.inputs.size() * 148 + txInfo.outputs.size() * 34 + 10;
        for (TxInput input : txInfo.inputs) {
            ISSet.put(txInfo, hashToShard(input));
        }
        for (TxInput output : txInfo.outputs) {
            OSSet.put(txInfo, hashToShard(output));
        }
        shardSet.addAll(ISSet.getGroup(txInfo));
        shardSet.addAll(OSSet.getGroup(txInfo));
        for (Integer shard : ISSet.getGroup(txInfo)) {
            Integer shardLeader = shard2Leader.get(shard);
            if (shardSet.size() == 1)
                currentNode.sendMessage(shardLeader, new ShardLeaderGetRequest(new IntraShardRequest(txInfo)), txSize);
            else
                currentNode.sendMessage(shardLeader, new ShardLeaderGetRequest(new InputLockRequest(txInfo)), txSize);
        }
    }
}
