package edu.pku.infosec.customized.detail;

import edu.pku.infosec.customized.NodeSigningState;
import edu.pku.infosec.event.NodeAction;
import edu.pku.infosec.node.Node;
import edu.pku.infosec.transaction.TxInfo;
import edu.pku.infosec.transaction.TxInput;

import java.util.Set;

import static edu.pku.infosec.customized.ModelData.*;


public class ShardLeaderStartCoSi implements NodeAction {
    private final TxInfo tx;
    private final CoSiType type;

    public ShardLeaderStartCoSi(TxInfo tx, CoSiType type) {
        this.tx = tx;
        this.type = type;
    }

    @Override
    public void runOn(Node currentNode) {
        final NodeSigningState state = getState(currentNode.getId(), tx);
        state.replyCounter = state.acceptCounter = 0;
        for (Integer groupLeader : shardLeader2GroupLeaders.getGroup(currentNode.getId())) {
            currentNode.sendMessage(groupLeader, new GroupLeaderGetAnnouncement(tx, type), 555);
        }
        currentNode.stayBusy(
                555, // don't forget time of signature verification
                new JudgeToSignOrNot(
                        tx, type,
                        new ShardLeaderCollectVotes(tx, type, 1, 1),
                        new ShardLeaderCollectVotes(tx, type, 0, 1)
                )
        );
    }
}

class GroupLeaderGetAnnouncement implements NodeAction {
    private final TxInfo tx;
    private final CoSiType type;

    public GroupLeaderGetAnnouncement(TxInfo tx, CoSiType type) {
        this.tx = tx;
        this.type = type;
    }

    @Override
    public void runOn(Node currentNode) {
        final NodeSigningState state = getState(currentNode.getId(), tx);
        state.replyCounter = state.acceptCounter = 0;
        for (Integer member : groupLeader2Members.getGroup(currentNode.getId())) {
            currentNode.sendMessage(member, new MemberGetAnnouncement(tx, type), 555);
        }
        currentNode.stayBusy(
                555, // don't forget time of signature verification
                new JudgeToSignOrNot(
                        tx, type,
                        new GroupLeaderCollectVote(tx, type, true),
                        new GroupLeaderCollectVote(tx, type, false)
                )
        );
    }
}

class MemberGetAnnouncement implements NodeAction {
    private final TxInfo tx;
    private final CoSiType type;

    public MemberGetAnnouncement(TxInfo tx, CoSiType type) {
        this.tx = tx;
        this.type = type;
    }

    @Override
    public void runOn(Node currentNode) {
        currentNode.stayBusy(
                555, // don't forget time of signature verification
                new JudgeToSignOrNot(
                        tx, type,
                        new MemberVoteFor(tx, type, true),
                        new MemberVoteFor(tx, type, false)
                )
        );
    }
}

class MemberVoteFor implements NodeAction {
    private final TxInfo tx;
    private final CoSiType type;
    private final boolean approval;

    public MemberVoteFor(TxInfo tx, CoSiType type, boolean approval) {
        this.tx = tx;
        this.type = type;
        this.approval = approval;
    }

    @Override
    public void runOn(Node currentNode) {
        currentNode.sendMessage(
                node2GroupLeader.get(currentNode.getId()),
                new GroupLeaderCollectVote(tx, type, approval),
                555
        );
    }
}

class GroupLeaderCollectVote implements NodeAction {
    private final TxInfo tx;
    private final CoSiType type;
    private final boolean approval;

    public GroupLeaderCollectVote(TxInfo tx, CoSiType type, boolean approval) {
        this.tx = tx;
        this.type = type;
        this.approval = approval;
    }

    @Override
    public void runOn(Node currentNode) {
        final NodeSigningState state = getState(currentNode.getId(), tx);
        if (approval)
            state.acceptCounter += 1;
        state.replyCounter += 1;
        int groupSize = groupLeader2GroupSize.get(currentNode.getId());
        if (state.replyCounter >= groupSize)
            currentNode.sendMessage(groupLeader2ShardLeader.get(currentNode.getId()),
                    new ShardLeaderCollectVotes(tx, type, state.acceptCounter, state.replyCounter),
                    555
            );
    }
}

class ShardLeaderCollectVotes implements NodeAction {
    private final TxInfo tx;
    private final CoSiType type;
    private final int acceptCnt, replyCnt;

    public ShardLeaderCollectVotes(TxInfo tx, CoSiType type, int acceptCnt, int replyCnt) {
        this.tx = tx;
        this.type = type;
        this.acceptCnt = acceptCnt;
        this.replyCnt = replyCnt;
    }

    @Override
    public void runOn(Node currentNode) {
        final NodeSigningState state = getState(currentNode.getId(), tx);
        state.acceptCounter += acceptCnt;
        state.replyCounter += replyCnt;
        int shardSize = shardLeader2ShardSize.get(currentNode.getId());
        if (state.replyCounter >= shardSize) {
            if (state.acceptCounter * 3 > shardSize * 2) {
                currentNode.stayBusy(555, new ShardLeaderStartChallenge(tx, type));
            } else {
                if (type == CoSiType.INPUT_LOCK_PREPARE)
                    new ShardLeaderStartCoSi(tx, CoSiType.INPUT_INVALID_PROOF).runOn(currentNode);
                else
                    new BroadcastViewInShard(new DiscardTransaction(tx), 555).runOn(currentNode);
            }
        }
    }
}

class DiscardTransaction implements NodeAction {
    private final TxInfo tx;

    public DiscardTransaction(TxInfo tx) {
        this.tx = tx;
    }

    @Override
    public void runOn(Node currentNode) {
        clearState(currentNode.getId(), tx);
    }
}