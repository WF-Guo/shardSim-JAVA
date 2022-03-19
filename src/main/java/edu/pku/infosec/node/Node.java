package edu.pku.infosec.node;

import edu.pku.infosec.customized.MyNetwork;
import edu.pku.infosec.event.EventDriver;
import edu.pku.infosec.event.NodeAction;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class Node {
    private final Network network;
    private final int id;
    private double nextIdleTime;
    private double totalBusyTime = 0;

    public final Map<Long, Integer> rollBackSignatureCnt;
    public final Map<Long, Integer> rollBackCnt;
    public final Map<Long, Integer> sonWaitCnt;
    public final Map<Long, Integer> verificationCnt;
    public final Set<Long> receiveCommitSet;

    Node(int id, Network network) {
        this.id = id;
        this.network = network;
        rollBackCnt = new HashMap<>();
        rollBackSignatureCnt = new HashMap<>();
        verificationCnt= new HashMap<>();
        sonWaitCnt = new HashMap<>();
        receiveCommitSet = new HashSet<>();
    }

    public int getId() {
        return id;
    }

    public void sendMessage(int to, NodeAction receivingAction, int size) {
        if (id == Network.EXTERNAL_ID || to == Network.EXTERNAL_ID)
            throw new RuntimeException("sendMessage() is for nodes");
        network.sendMessage(id, to, receivingAction, size);
    }

    public void sendOut(NodeAction receivingAction) {
        network.sendOut(receivingAction);
    }

    public void sendIn(int id, NodeAction receivingAction) {
        if (this.id != Network.EXTERNAL_ID)
            throw new RuntimeException("sendIn() is for client");
        network.sendIn(id, receivingAction);
    }

    public void sendToOverlapLeader(int firstshard, int secondshard, NodeAction receivingAction, int size)
    {
        if(this.id == -1)
            throw new RuntimeException("sendToOverlapLeader() is for nodes");
        ((MyNetwork) network).sendToOverlapLeader(id, firstshard, secondshard, receivingAction, size);
    }

    public void sendToHalfOriginalShard
            (int originalShard, int hash, NodeAction receivingAction, int size)
    {
        if(this.id == -1)
            throw new RuntimeException("sendToHalfOriginalShard() is for nodes");
        ((MyNetwork) network).sendToHalfOriginalShard(id, hash, originalShard, receivingAction, size);
    }

    public void sendToOriginalShard
            (int originalShard, NodeAction receivingAction, int size) {
        if(this.id == -1)
            throw new RuntimeException("sendToOriginalShard() is for nodes");
        ((MyNetwork) network).sendToOriginalShard(id, originalShard, receivingAction, size);
    }

    public int sendToTreeSons(NodeAction receivingAction, int size) {
        if(this.id == -1)
            throw new RuntimeException("sendToTreeSons() is for nodes");
        return ((MyNetwork) network).sendToTreeSons(id, receivingAction, size);
    }

    public int sendToTreeParent(NodeAction receivingAction, int size) {
        if(this.id == -1)
            throw new RuntimeException("sendToTreeParent() is for nodes");
        return ((MyNetwork) network).sendToTreeParent(id, receivingAction, size);
    }

    public double getNextIdleTime() {
        return nextIdleTime;
    }

    public double getTotalBusyTime() {
        return totalBusyTime;
    }

    public void stayBusy(double busyTime, NodeAction nextAction) {
        if (nextIdleTime > EventDriver.getCurrentTime())
            throw new RuntimeException("Calling stayBusy more than once in one function");
        nextIdleTime = EventDriver.getCurrentTime() + busyTime;
        EventDriver.insertEvent(nextIdleTime, this, nextAction);
        totalBusyTime += busyTime;
    }
}
