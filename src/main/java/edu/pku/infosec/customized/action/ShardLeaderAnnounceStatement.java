package edu.pku.infosec.customized.action;

import edu.pku.infosec.customized.NodeSigningState;
import edu.pku.infosec.customized.Signable;
import edu.pku.infosec.customized.VerificationResult;
import edu.pku.infosec.event.EventDriver;
import edu.pku.infosec.event.NodeAction;
import edu.pku.infosec.node.Node;

import static edu.pku.infosec.customized.ModelData.*;

public class ShardLeaderAnnounceStatement implements NodeAction {
    private final Signable statement;

    public ShardLeaderAnnounceStatement(Signable statement) {
        this.statement = statement;
    }

    @Override
    public void runOn(Node currentNode) {
        final NodeSigningState state = getState(currentNode);
        state.replyCounter = state.acceptCounter = 1;
        state.admitted = true;
        for (Integer groupLeader : shardLeader2GroupLeaders.getGroup(currentNode.getId())) {
            currentNode.sendMessage(groupLeader, new GroupLeaderGetAnnouncement(statement), statement.getSize());
        }
    }
}

class GroupLeaderGetAnnouncement implements NodeAction {
    private final Signable statement;

    public GroupLeaderGetAnnouncement(Signable statement) {
        this.statement = statement;
    }

    @Override
    public void runOn(Node currentNode) {
        final NodeSigningState state = getState(currentNode);
        state.replyCounter = state.acceptCounter = 0;
        for (Integer member : groupLeader2Members.getGroup(currentNode.getId())) {
            currentNode.sendMessage(member, new MemberGetAnnouncement(statement), statement.getSize());
        }
        final VerificationResult result = statement.verifyOn(currentNode);
        state.admitted = result.passed;
        currentNode.stayBusy(result.timeCost, new GroupLeaderCollectVote(statement, result.passed));
    }
}

class MemberGetAnnouncement implements NodeAction {
    private final Signable statement;

    public MemberGetAnnouncement(Signable statement) {
        this.statement = statement;
    }

    @Override
    public void runOn(Node currentNode) {
        final NodeSigningState state = getState(currentNode);
        final VerificationResult result = statement.verifyOn(currentNode);
        state.admitted = result.passed;
        currentNode.stayBusy(result.timeCost, new MemberVoteFor(statement, result.passed));
    }
}

class MemberVoteFor implements NodeAction {
    private final Signable statement;
    private final boolean approval;

    public MemberVoteFor(Signable statement, boolean approval) {
        this.statement = statement;
        this.approval = approval;
    }

    @Override
    public void runOn(Node currentNode) {
        currentNode.sendMessage(
                node2GroupLeader.get(currentNode.getId()),
                new GroupLeaderCollectVote(statement, approval),
                approval ? ECDSA_POINT_SIZE : 1
        );
    }
}

class GroupLeaderCollectVote implements NodeAction {
    private final Signable statement;
    private final boolean approval;

    public GroupLeaderCollectVote(Signable statement, boolean approval) {
        this.statement = statement;
        this.approval = approval;
    }

    @Override
    public void runOn(Node currentNode) {
        final NodeSigningState state = getState(currentNode);
        if (approval)
            state.acceptCounter += 1;
        state.replyCounter += 1;
        int groupSize = groupLeader2GroupSize.get(currentNode.getId());
        if (state.replyCounter >= groupSize) {
            currentNode.stayBusy(
                    ECDSA_POINT_ADD_TIME * state.acceptCounter + // aggregate
                            (state.acceptCounter * HASH_SIZE + ECDSA_POINT_SIZE) * BYTE_HASH_TIME, // merkel
                    new GroupLeaderAggregateCommits(statement)
            );
        }
    }
}

class GroupLeaderAggregateCommits implements NodeAction {
    private final Signable statement;

    public GroupLeaderAggregateCommits(Signable statement) {
        this.statement = statement;
    }

    @Override
    public void runOn(Node currentNode) {
        final NodeSigningState state = getState(currentNode);
        currentNode.sendMessage(groupLeader2ShardLeader.get(currentNode.getId()),
                new ShardLeaderCollectVotes(statement, state.acceptCounter, state.replyCounter),
                HASH_SIZE /*Merkle root*/ + ECDSA_POINT_SIZE /*commit*/ +
                        (state.replyCounter - state.acceptCounter) * ECDSA_POINT_SIZE /*absent list*/
        );
    }
}

class ShardLeaderCollectVotes implements NodeAction {
    private final Signable statement;
    private final int acceptCnt;
    private final int replyCnt;

    public ShardLeaderCollectVotes(Signable statement, int acceptCnt, int replyCnt) {
        this.statement = statement;
        this.acceptCnt = acceptCnt;
        this.replyCnt = replyCnt;
    }

    @Override
    public void runOn(Node currentNode) {
        final NodeSigningState state = getState(currentNode);
        final int groupNumber = shardLeader2GroupLeaders.getGroup(currentNode.getId()).size();
        state.acceptCounter += acceptCnt;
        state.replyCounter += replyCnt;
        int shardSize = shardLeader2ShardSize.get(currentNode.getId());
        if (state.replyCounter >= shardSize) {
            if (state.acceptCounter * 3 > shardSize * 2) {
                currentNode.stayBusy(
                        groupNumber * ECDSA_POINT_ADD_TIME + // aggregate
                                (groupNumber * HASH_SIZE + ECDSA_POINT_SIZE) * BYTE_HASH_TIME + // merkel tree root
                                BYTE_HASH_TIME * (HASH_SIZE + ECDSA_POINT_SIZE), // challenge
                        new ShardLeaderStartChallenge(statement)
                );
            } else {
                throw new RuntimeException("Intra-shard consensus failed.");
            }
        }
    }
}

class ShardLeaderStartChallenge implements NodeAction {
    private final Signable statement;

    public ShardLeaderStartChallenge(Signable statement) {
        this.statement = statement;
    }

    @Override
    public void runOn(Node currentNode) {
        final NodeSigningState state = getState(currentNode);
        final int absentNum = shardLeader2ShardSize.get(currentNode.getId()) - state.acceptCounter;
        state.replyCounter = 0;
        if (state.admitted)
            state.replyCounter++;
        for (Integer groupLeader : shardLeader2GroupLeaders.getGroup(currentNode.getId())) {
            currentNode.sendMessage(groupLeader, new GroupLeaderGetChallenge(statement), HASH_SIZE);
        }
        currentNode.stayBusy(ECDSA_POINT_ADD_TIME * absentNum, n -> {
        }); // calc pubKey sum
    }
}

class GroupLeaderGetChallenge implements NodeAction {
    private final Signable statement;

    public GroupLeaderGetChallenge(Signable statement) {
        this.statement = statement;
    }

    @Override
    public void runOn(Node currentNode) {
        final NodeSigningState state = getState(currentNode);
        state.replyCounter = 0;
        if (state.admitted)
            state.replyCounter++;
        for (Integer member : groupLeader2Members.getGroup(currentNode.getId())) {
            currentNode.sendMessage(member, new MemberGetChallenge(statement), HASH_SIZE);
        }
    }
}

class MemberGetChallenge implements NodeAction {
    private final Signable statement;

    public MemberGetChallenge(Signable statement) {
        this.statement = statement;
    }

    @Override
    public void runOn(Node currentNode) {
        final NodeSigningState state = getState(currentNode);
        if (state.admitted)
            currentNode.sendMessage(node2GroupLeader.get(currentNode.getId()),
                    new GroupLeaderCollectResponse(statement), ECDSA_NUMBER_SIZE);
    }
}

class GroupLeaderCollectResponse implements NodeAction {
    private final Signable statement;

    public GroupLeaderCollectResponse(Signable statement) {
        this.statement = statement;
    }

    @Override
    public void runOn(Node currentNode) {
        final NodeSigningState state = getState(currentNode);
        state.replyCounter += 1;
        if (state.replyCounter == state.acceptCounter && state.acceptCounter > 0) {
            currentNode.sendMessage(groupLeader2ShardLeader.get(currentNode.getId()),
                    new ShardLeaderCollectResponse(statement, state.replyCounter), ECDSA_NUMBER_SIZE);
        }
    }
}

class ShardLeaderCollectResponse implements NodeAction {
    private final Signable statement;
    private final int replyCnt;

    public ShardLeaderCollectResponse(Signable statement, int replyCnt) {
        this.statement = statement;
        this.replyCnt = replyCnt;
    }

    @Override
    public void runOn(Node currentNode) {
        NodeSigningState state = getState(currentNode);
        final int absentNum = shardLeader2ShardSize.get(currentNode.getId()) - state.acceptCounter;
        state.replyCounter += replyCnt;
        if (state.replyCounter >= state.acceptCounter) {
            currentNode.stayBusy(
                    ECDSA_POINT_MUL_TIME * 2 + ECDSA_POINT_ADD_TIME,
                    statement.actionAfterSigning(absentNum, node2Shard.get(currentNode.getId()))
            );
        }
    }
}
