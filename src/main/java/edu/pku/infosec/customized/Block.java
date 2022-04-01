package edu.pku.infosec.customized;

import edu.pku.infosec.customized.action.ShardLeaderAnnounceStatement;
import edu.pku.infosec.customized.request.Request;
import edu.pku.infosec.event.NodeAction;
import edu.pku.infosec.node.Node;

import java.util.ArrayList;
import java.util.List;

import static edu.pku.infosec.customized.ModelData.HASH_SIZE;
import static edu.pku.infosec.customized.ModelData.maliciousNodes;

public class Block implements Signable {
    private final List<Request> requestList = new ArrayList<>();
    private int size = 0;

    public void addRequest(Request statement) {
        size += statement.getSize();
        requestList.add(statement);
    }

    public int getSize() {
        return size;
    }

    public List<Request> getRequestList() {
        return requestList;
    }

    @Override
    public VerificationResult verifyOn(Node currentNode) {
        if (maliciousNodes.contains(currentNode.getId())) {
            return new VerificationResult(0, false);
        }
        double totalTimeCost = 0;
        for (Request request : requestList) {
            final VerificationResult result = request.verifyOn(currentNode);
            totalTimeCost += result.timeCost;
            if (!result.passed) {
                return new VerificationResult(totalTimeCost, false);
            }
        }
        return new VerificationResult(totalTimeCost, true);
    }

    @Override
    public NodeAction actionAfterSigning(int absentNum, int shardId) {
        return new ShardLeaderAnnounceStatement(new CollectivelySignedMessage(absentNum, HASH_SIZE, shardId));
    }
}
