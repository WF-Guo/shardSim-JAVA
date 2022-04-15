package edu.pku.infosec.node;

import edu.pku.infosec.customized.MyNetwork;
import edu.pku.infosec.event.EventDriver;
import edu.pku.infosec.event.NodeAction;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static edu.pku.infosec.node.Network.EXTERNAL_ID;

public class Node {
    private final Network network;
    private final int id;
    private double nextIdleTime;
    private double totalBusyTime = 0;

    public final Map<Long, Integer> rollBackSignatureCnt;
    public final Map<Long, Integer> rollBackCnt;
    public final Map<Long, Integer> sonWaitCnt;
    public final Map<Long, Integer> obsentCnt;

    Node(int id, Network network) {
        this.id = id;
        this.network = network;
        rollBackCnt = new HashMap<>();
        rollBackSignatureCnt = new HashMap<>();
        obsentCnt= new HashMap<>();
        sonWaitCnt = new HashMap<>();
    }

    public int getId() {
        return id;
    }

    public void sendMessage(int to, NodeAction receivingAction, int size) {
        if (id == EXTERNAL_ID || to == EXTERNAL_ID)
            throw new RuntimeException("sendMessage() is for nodes");
        network.sendMessage(id, to, receivingAction, size);
    }

    public void sendOut(NodeAction receivingAction) {
        network.sendOut(receivingAction);
    }

    public void sendIn(int id, NodeAction receivingAction) {
        if (this.id != EXTERNAL_ID)
            throw new RuntimeException("sendIn() is for client");
        network.sendIn(id, receivingAction);
    }

    public void sendToOverlapLeader(int firstshard, int secondshard, NodeAction receivingAction, int size)
    {
        if(this.id == -1)
            throw new RuntimeException("sendToOverlapLeader() is for nodes");
        ((MyNetwork) network).sendToOverlapLeader(id, firstshard, secondshard, receivingAction, size);
    }

    public void sendToOverlapShard(int firstshard, int secondshard, NodeAction receivingAction, int size)
    {
        if(this.id == -1)
            throw new RuntimeException("sendToOverlapShard() is for nodes");
        ((MyNetwork) network).sendToOverlapShard(id, firstshard, secondshard, receivingAction, size);
    }

    public void leaderUpdateShard(NodeAction receivingAction, int size)
    {
        if(this.id == -1)
            throw new RuntimeException("leaderUpdateShard() is for nodes");
        ((MyNetwork) network).leaderUpdateShard(id, receivingAction, size);
    }

    public void sendToHalfOriginalShard
            (int originalShard, int hash, NodeAction receivingAction, int size)
    {
        if(this.id == -1)
            throw new RuntimeException("sendToHalfOriginalShard() is for nodes");
        ((MyNetwork) network).sendToHalfOriginalShard(id, originalShard, hash, receivingAction, size);
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
        if (nextIdleTime > EventDriver.getCurrentTime() && id != EXTERNAL_ID)
            throw new RuntimeException("Calling stayBusy more than once in one function");
        nextIdleTime = EventDriver.getCurrentTime() + busyTime;
        EventDriver.insertEvent(nextIdleTime, this, nextAction);
        totalBusyTime += busyTime;
    }
}
