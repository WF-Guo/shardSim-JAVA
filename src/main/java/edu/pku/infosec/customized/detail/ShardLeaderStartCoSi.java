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
        final int messageSize = tx.inputs.size() * INPUT_SIZE + tx.outputs.size() * OUTPUT_SIZE + TX_OVERHEAD_SIZE + 1;
        state.replyCounter = state.acceptCounter = 0;
        for (Integer groupLeader : shardLeader2GroupLeaders.getGroup(currentNode.getId())) {
            currentNode.sendMessage(groupLeader, new GroupLeaderGetAnnouncement(tx, type), messageSize);
        }
        new JudgeToSignOrNot(
                tx, type,
                new ShardLeaderCollectVotes(tx, type, 1, 1),
                new ShardLeaderCollectVotes(tx, type, 0, 1)
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
        final int messageSize = tx.inputs.size() * INPUT_SIZE + tx.outputs.size() * OUTPUT_SIZE + TX_OVERHEAD_SIZE + 1;
        state.replyCounter = state.acceptCounter = 0;
        for (Integer member : groupLeader2Members.getGroup(currentNode.getId())) {
            currentNode.sendMessage(member, new MemberGetAnnouncement(tx, type), messageSize);
        }
        new JudgeToSignOrNot(
                tx, type,
                new GroupLeaderCollectVote(tx, type, true),
                new GroupLeaderCollectVote(tx, type, false)
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
        new JudgeToSignOrNot(
                tx, type,
                new MemberVoteFor(tx, type, true),
                new MemberVoteFor(tx, type, false)
        ).runOn(currentNode);
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
                HASH_SIZE + 1 + (approval ? ECDSA_POINT_SIZE : 0)
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
            currentNode.stayBusy(
                    ECDSA_POINT_ADD_TIME * state.acceptCounter + // aggregate
                            (state.acceptCounter * HASH_SIZE + ECDSA_POINT_SIZE) * BYTE_HASH_TIME, // merkel
                    new GroupLeaderAggregateCommits(tx, type)
            );
    }
}

class GroupLeaderAggregateCommits implements NodeAction {
    private final TxInfo tx;
    private final CoSiType type;

    public GroupLeaderAggregateCommits(TxInfo tx, CoSiType type) {
        this.tx = tx;
        this.type = type;
    }

    @Override
    public void runOn(Node currentNode) {
        final NodeSigningState state = getState(currentNode.getId(), tx);
        currentNode.sendMessage(groupLeader2ShardLeader.get(currentNode.getId()),
                new ShardLeaderCollectVotes(tx, type, state.acceptCounter, state.replyCounter),
                HASH_SIZE /*id*/ + 1 + HASH_SIZE /*Merkle root*/ + ECDSA_POINT_SIZE /*commit*/ +
                        (state.replyCounter - state.acceptCounter) * ECDSA_POINT_SIZE /*absent list*/
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
        final int groupNumber = shardLeader2GroupLeaders.getGroup(currentNode.getId()).size();
        final int txSize = tx.inputs.size() * INPUT_SIZE + tx.outputs.size() * OUTPUT_SIZE + TX_OVERHEAD_SIZE;
        state.acceptCounter += acceptCnt;
        state.replyCounter += replyCnt;
        int shardSize = shardLeader2ShardSize.get(currentNode.getId());
        if (state.replyCounter >= shardSize) {
            if (state.acceptCounter * 3 > shardSize * 2) {
                currentNode.stayBusy(
                        groupNumber * ECDSA_POINT_ADD_TIME + // aggregate
                                (groupNumber * HASH_SIZE + ECDSA_POINT_SIZE) * BYTE_HASH_TIME + // merkel tree root
                                BYTE_HASH_TIME * (txSize + ECDSA_POINT_SIZE), // challenge
                        new ShardLeaderStartChallenge(tx, type)
                );
            } else {
                if (type == CoSiType.INPUT_LOCK_PREPARE)
                    new ShardLeaderStartCoSi(tx, CoSiType.INPUT_INVALID_PROOF).runOn(currentNode);
                else
                    new BroadcastViewInShard(new DiscardTransaction(tx), HASH_SIZE + 1).runOn(currentNode);
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
        final int shardId = node2Shard.get(currentNode.getId());
        final Set<TxInput> uncommittedInputs = uncommittedInputsOnNode.getGroup(currentNode.getId());
        final NodeSigningState state = getState(currentNode.getId(), tx);
        if (state.admitted) {
            for (TxInput input : tx.inputs) {
                if (getShardId(input) == shardId) {
                    uncommittedInputs.remove(input);
                }
            }
        }
        clearState(currentNode.getId(), tx);
    }
}