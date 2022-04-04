package edu.pku.infosec.customized;

import edu.pku.infosec.customized.action.BroadcastViewInShard;
import edu.pku.infosec.customized.request.Request;
import edu.pku.infosec.event.NodeAction;
import edu.pku.infosec.node.Node;

import static edu.pku.infosec.customized.ModelData.*;

public class CollectivelySignedMessage implements Signable {
    public final int sourceShard;
    private final int absentListLength;
    private final int messageSize;

    public CollectivelySignedMessage(int absentListLength, int messageSize, int sourceShard) {
        this.absentListLength = absentListLength;
        this.messageSize = messageSize;
        this.sourceShard = sourceShard;
    }

    public int getSize() {
        return messageSize + absentListLength * ECDSA_POINT_SIZE + ECDSA_NUMBER_SIZE + HASH_SIZE;
    }

    public VerificationResult verifyOn(Node currentNode) {
        final double pubKeySumCalcTime = absentListLength * ECDSA_POINT_ADD_TIME;
        final double commitKeyCalcTime = 2 * ECDSA_POINT_MUL_TIME + ECDSA_POINT_ADD_TIME;
        final double checkHashTime = BYTE_HASH_TIME * (ECDSA_POINT_SIZE + messageSize);
        return new VerificationResult(pubKeySumCalcTime + commitKeyCalcTime + checkHashTime, true);
    }

    @Override
    public NodeAction actionAfterSigning(int absentNum, int shardId) {
        return currentNode -> {
            clearState(currentNode, this);
            final Block block = prepareCoSi2Block.remove(this);
            final int requestNum = block.getRequestList().size();
            final int treeDepth = (int) (Math.ceil(Math.log(requestNum - 0.000001) / Math.log(2)));
            final int fullDepthRange = treeDepth == 0 ? 1 : ((requestNum - (1 << (treeDepth - 1))) * 2);
            for (int i = 0; i < block.getRequestList().size(); i++) {
                final int depth = i < fullDepthRange ? treeDepth : (treeDepth - 1);
                final Request request = block.getRequestList().get(i);
                request.commitInShard(shardId);
                final CollectivelySignedMessage proof =
                        new CollectivelySignedMessage(absentNum, HASH_SIZE * (depth + 1), shardId);
                currentNode.sendMessage(
                        ClientAccessPoint.get(request.tx),
                        new ReturnProofToClient(request, proof),
                        proof.getSize()
                );
            }
            // Spend commit time
            final CollectivelySignedMessage blockProof = new CollectivelySignedMessage(absentNum, HASH_SIZE, shardId);
            new BroadcastViewInShard(new CommitBlockLocally(block, blockProof), blockProof.getSize()).runOn(currentNode);
        };
    }
}

class ReturnProofToClient implements NodeAction {
    private final Request request;
    private final CollectivelySignedMessage proof;

    public ReturnProofToClient(Request request, CollectivelySignedMessage proof) {
        this.request = request;
        this.proof = proof;
    }

    @Override
    public void runOn(Node currentNode) {
        currentNode.sendOut(new ClientReceiveProof(request, proof));
    }
}


class ClientReceiveProof implements NodeAction {
    private final Request request;
    private final CollectivelySignedMessage proof;

    public ClientReceiveProof(Request request, CollectivelySignedMessage proof) {
        this.request = request;
        this.proof = proof;
    }

    @Override
    public void runOn(Node currentNode) {
        final VerificationResult result = proof.verifyOn(currentNode);
        currentNode.stayBusy(result.timeCost, node -> request.afterCheckingResponse(node, proof));
    }
}

class CommitBlockLocally implements NodeAction {
    private final Block block;
    private final CollectivelySignedMessage proof;

    public CommitBlockLocally(Block block, CollectivelySignedMessage proof) {
        this.block = block;
        this.proof = proof;
    }

    @Override
    public void runOn(Node currentNode) {
        double timeCost = proof.verifyOn(currentNode).timeCost;
        for (Request request : block.getRequestList()) {
            timeCost += request.commitTime(currentNode);
        }
        currentNode.stayBusy(timeCost, n -> {
        });
    }
}
