package edu.pku.infosec.customized.action;

import edu.pku.infosec.customized.Block;
import edu.pku.infosec.customized.NodeSigningState;
import edu.pku.infosec.customized.VerificationResult;
import edu.pku.infosec.customized.request.InputLockRequest;
import edu.pku.infosec.customized.request.OutputCommitRequest;
import edu.pku.infosec.customized.request.ProofOfRejectionRequest;
import edu.pku.infosec.customized.request.Request;
import edu.pku.infosec.event.NodeAction;
import edu.pku.infosec.node.Node;

import java.util.LinkedList;
import java.util.Queue;

import static edu.pku.infosec.customized.ModelData.*;

public class ShardLeaderGetRequest implements NodeAction {
    private final Request request;

    public ShardLeaderGetRequest(Request request) {
        this.request = request;
    }

    @Override
    public void runOn(Node currentNode) {
        // Select UTXO in set and run script to validate signature
        final VerificationResult result = request.verifyOn(currentNode);
        if (result.passed) {
            currentNode.stayBusy(result.timeCost, new ShardLeaderAcceptRequest(request));
        } else if (request.getClass() == InputLockRequest.class) {
            currentNode.stayBusy(result.timeCost, new ShardLeaderAcceptRequest(new ProofOfRejectionRequest(request.tx)));
        }
    }
}

class ShardLeaderAcceptRequest implements NodeAction {
    private final Request entry;

    public ShardLeaderAcceptRequest(Request entry) {
        this.entry = entry;
    }

    @Override
    public void runOn(Node currentNode) {
        shard2CurrentBlock.putIfAbsent(node2Shard.get(currentNode.getId()), new Block());
        final Block block = shard2CurrentBlock.get(node2Shard.get(currentNode.getId()));
        leader2WaitingQueue.putIfAbsent(currentNode, new LinkedList<>());
        final Queue<Request> waitingQueue = leader2WaitingQueue.get(currentNode);
        if (waitingQueue.isEmpty()) {
            if (block.getSize() + entry.getSize() > BLOCK_SIZE) {
                waitingQueue.add(entry);
                final NodeSigningState state = getState(currentNode);
                state.replyCounter = state.acceptCounter = 0;
                // sign on merkle root
                int hashedHashNum = block.getRequestList().size() - 2; // exclude leafs and root
                currentNode.stayBusy(
                        BYTE_HASH_TIME * (block.getSize() + HASH_SIZE * hashedHashNum),
                        new ShardLeaderAnnounceStatement(block)
                );
            } else block.addRequest(entry);
        } else waitingQueue.add(entry);
    }
}
