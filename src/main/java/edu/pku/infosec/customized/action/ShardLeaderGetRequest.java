package edu.pku.infosec.customized.action;

import edu.pku.infosec.customized.Block;
import edu.pku.infosec.customized.NodeSigningState;
import edu.pku.infosec.customized.VerificationResult;
import edu.pku.infosec.customized.request.InputLockRequest;
import edu.pku.infosec.customized.request.ProofOfRejectionRequest;
import edu.pku.infosec.customized.request.Request;
import edu.pku.infosec.event.NodeAction;
import edu.pku.infosec.node.Node;

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
    private final Request request;

    public ShardLeaderAcceptRequest(Request request) {
        this.request = request;
    }

    @Override
    public void runOn(Node currentNode) {
        shardLeader2AssemblingBlock.putIfAbsent(currentNode, new Block());
        Block block = shardLeader2AssemblingBlock.get(currentNode);
        if (block.getSize() + request.getSize() > BLOCK_SIZE) {
            final Block assembledBlock = block;
            block = new Block();
            shardLeader2AssemblingBlock.put(currentNode, block);
            final NodeSigningState state = getState(currentNode, assembledBlock);
            state.replyCounter = state.acceptCounter = 0;
            // sign on merkle root
            int hashedHashNum = assembledBlock.getRequestList().size() - 2; // exclude leafs and root
            currentNode.stayBusy(
                    BYTE_HASH_TIME * (assembledBlock.getSize() + HASH_SIZE * hashedHashNum),
                    new ShardLeaderAnnounceStatement(assembledBlock)
            );
        }
        block.addRequest(request);
    }
}
