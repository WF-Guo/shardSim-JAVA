package edu.pku.infosec.customized.detail;

import edu.pku.infosec.customized.ModelData;
import edu.pku.infosec.customized.NodeSigningState;
import edu.pku.infosec.event.NodeAction;
import edu.pku.infosec.node.Node;
import edu.pku.infosec.transaction.TxInfo;

public class ShardLeaderStartChallenge implements NodeAction {
    private final TxInfo tx;
    private final CoSiType type;

    public ShardLeaderStartChallenge(TxInfo tx, CoSiType type) {
        this.tx = tx;
        this.type = type;
    }

    @Override
    public void runOn(Node currentNode) {
        final NodeSigningState state = ModelData.getState(currentNode.getId(), tx);
        state.replyCounter = 0;
        if (state.admitted)
            state.replyCounter++;
        for (Integer groupLeader : ModelData.shardLeader2GroupLeaders.getGroup(currentNode.getId())) {
            currentNode.sendMessage(groupLeader, new GroupLeaderGetChallenge(tx, type), 555);
        }
    }
}

class GroupLeaderGetChallenge implements NodeAction {
    private final TxInfo tx;
    private final CoSiType type;

    public GroupLeaderGetChallenge(TxInfo tx, CoSiType type) {
        this.tx = tx;
        this.type = type;
    }

    @Override
    public void runOn(Node currentNode) {
        final NodeSigningState state = ModelData.getState(currentNode.getId(), tx);
        state.replyCounter = 0;
        if (state.admitted)
            state.replyCounter++;
        for (Integer member : ModelData.groupLeaderToMembers.getGroup(currentNode.getId())) {
            currentNode.sendMessage(member, new MemberGetChallenge(tx, type), 555);
        }
    }
}

class MemberGetChallenge implements NodeAction {
    private final TxInfo tx;
    private final CoSiType type;

    public MemberGetChallenge(TxInfo tx, CoSiType type) {
        this.tx = tx;
        this.type = type;
    }

    @Override
    public void runOn(Node currentNode) {
        final NodeSigningState state = ModelData.getState(currentNode.getId(), tx);
        if (state.admitted)
            currentNode.sendMessage(ModelData.node2GroupLeader.get(currentNode.getId()),
                    new GroupLeaderCollectCoSi(tx, type), 555);
    }
}

class GroupLeaderCollectCoSi implements NodeAction {
    private final TxInfo tx;
    private final CoSiType type;

    public GroupLeaderCollectCoSi(TxInfo tx, CoSiType type) {
        this.tx = tx;
        this.type = type;
    }

    @Override
    public void runOn(Node currentNode) {
        final NodeSigningState state = ModelData.getState(currentNode.getId(), tx);
        state.replyCounter += 1;
        if (state.replyCounter == state.acceptCounter && state.acceptCounter > 0) {
            currentNode.stayBusy(555, new GroupLeaderCommitCoSi(tx, type));
        }
    }
}

class GroupLeaderCommitCoSi implements NodeAction {
    private final TxInfo tx;
    private final CoSiType type;

    public GroupLeaderCommitCoSi(TxInfo tx, CoSiType type) {
        this.tx = tx;
        this.type = type;
    }

    @Override
    public void runOn(Node currentNode) {
        final NodeSigningState state = ModelData.getState(currentNode.getId(), tx);
        currentNode.sendMessage(ModelData.groupLeader2ShardLeader.get(currentNode.getId()),
                new ShardLeaderCollectCoSi(tx, type, state.replyCounter), 555);
        ModelData.clearState(currentNode.getId(), tx);
    }
}


class ShardLeaderCollectCoSi implements NodeAction {
    private final TxInfo tx;
    private final CoSiType type;
    private final int replyCnt;

    public ShardLeaderCollectCoSi(TxInfo tx, CoSiType type, int replyCnt) {
        this.tx = tx;
        this.type = type;
        this.replyCnt = replyCnt;
    }

    @Override
    public void runOn(Node currentNode) {
        NodeSigningState state = ModelData.getState(currentNode.getId(), tx);
        state.replyCounter += replyCnt;
        if (state.replyCounter >= state.acceptCounter)
            currentNode.stayBusy(555, new ShardLeaderFinishCoSi(tx, type));
    }
}

