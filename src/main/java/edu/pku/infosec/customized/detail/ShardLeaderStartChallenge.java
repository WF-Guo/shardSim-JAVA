package edu.pku.infosec.customized.detail;

import edu.pku.infosec.customized.NodeSigningState;
import edu.pku.infosec.event.NodeAction;
import edu.pku.infosec.node.Node;
import edu.pku.infosec.transaction.TxInfo;

import static edu.pku.infosec.customized.ModelData.*;

public class ShardLeaderStartChallenge implements NodeAction {
    private final TxInfo tx;
    private final CoSiType type;

    public ShardLeaderStartChallenge(TxInfo tx, CoSiType type) {
        this.tx = tx;
        this.type = type;
    }

    @Override
    public void runOn(Node currentNode) {
        final NodeSigningState state = getState(currentNode.getId(), tx);
        state.replyCounter = 0;
        if (state.admitted)
            state.replyCounter++;
        for (Integer groupLeader : shardLeader2GroupLeaders.getGroup(currentNode.getId())) {
            currentNode.sendMessage(groupLeader, new GroupLeaderGetChallenge(tx, type), 2 * HASH_SIZE + 1);
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
        final NodeSigningState state = getState(currentNode.getId(), tx);
        final int messageSize = HASH_SIZE;
        state.replyCounter = 0;
        if (state.admitted)
            state.replyCounter++;
        for (Integer member : groupLeader2Members.getGroup(currentNode.getId())) {
            currentNode.sendMessage(member, new MemberGetChallenge(tx, type), 2 * HASH_SIZE + 1);
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
        final NodeSigningState state = getState(currentNode.getId(), tx);
        if (state.admitted)
            currentNode.sendMessage(node2GroupLeader.get(currentNode.getId()),
                    new GroupLeaderCollectCoSi(tx, type), HASH_SIZE + 1 + ECDSA_NUMBER_SIZE);
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
        final NodeSigningState state = getState(currentNode.getId(), tx);
        state.replyCounter += 1;
        if (state.replyCounter == state.acceptCounter && state.acceptCounter > 0) {
            currentNode.sendMessage(groupLeader2ShardLeader.get(currentNode.getId()),
                    new ShardLeaderCollectCoSi(tx, type, state.replyCounter), HASH_SIZE + 1 + ECDSA_NUMBER_SIZE);
            clearState(currentNode.getId(), tx);
        }
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
        NodeSigningState state = getState(currentNode.getId(), tx);
        state.replyCounter += replyCnt;
        if (state.replyCounter >= state.acceptCounter)
            currentNode.stayBusy(
                    ECDSA_POINT_MUL_TIME * 2 + ECDSA_POINT_ADD_TIME,
                    new ShardLeaderFinishCoSi(tx, type)
            );
    }
}

