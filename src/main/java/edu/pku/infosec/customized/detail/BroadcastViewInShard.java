package edu.pku.infosec.customized.detail;

import edu.pku.infosec.customized.ModelData;
import edu.pku.infosec.event.NodeAction;
import edu.pku.infosec.node.Node;

public class BroadcastViewInShard implements NodeAction {
    NodeAction localChange;
    int messageSize;

    public BroadcastViewInShard(NodeAction localChange, int messageSize) {
        this.localChange = localChange;
        this.messageSize = messageSize;
    }

    @Override
    public void runOn(Node currentNode) {
        for (int groupLeader : ModelData.shardLeader2GroupLeaders.getGroup(currentNode.getId()))
            currentNode.sendMessage(groupLeader, new BroadcastViewInGroup(localChange, messageSize), messageSize);
        localChange.runOn(currentNode);
    }
}

class BroadcastViewInGroup implements NodeAction {
    NodeAction localChange;
    int messageSize;

    public BroadcastViewInGroup(NodeAction localChange, int messageSize) {
        this.localChange = localChange;
        this.messageSize = messageSize;
    }

    @Override
    public void runOn(Node currentNode) {
        for(int member: ModelData.groupLeader2Members.getGroup(currentNode.getId()))
            currentNode.sendMessage(member, localChange, messageSize);
        localChange.runOn(currentNode);
    }
}