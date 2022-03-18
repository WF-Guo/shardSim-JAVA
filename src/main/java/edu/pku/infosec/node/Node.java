package edu.pku.infosec.node;

import edu.pku.infosec.customized.MyNetwork;
import edu.pku.infosec.event.Event;
import edu.pku.infosec.event.EventDriver;
import edu.pku.infosec.event.EventHandler;
import edu.pku.infosec.event.EventParam;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class Node {
    private double nextIdleTime;
    private final Network network;
    private final int id;

    public final Map<Long, Integer> rollBackSignatureCnt;
    public final Map<Long, Integer> rollBackCnt;
    public final Map<Long, Integer> sonWaitCnt;
    public final Map<Long, Integer> verificationCnt;
    public final Set<Long> receiveCommitSet;
    public long totalBusyTime = 0;

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

    public void sendMessage(int to, EventHandler receivingAction, EventParam data, int size) {
        if(id == -1)
            throw new RuntimeException("sendMessage() is for nodes");
        network.sendMessage(id, to, receivingAction, data, size);
    }

    public void sendOut(EventHandler receivingAction, EventParam data) {
        network.sendOut(receivingAction, data);
    }

    public void sendIn(int id, EventHandler receivingAction, EventParam data) {
        if(this.id != -1)
            throw new RuntimeException("sendIn() is for client");
        network.sendIn(id,receivingAction, data);
    }

    public void sendToVirtualShard(int shard, EventHandler receivingAction,
                                  EventParam data, int size)
    {
        if(this.id == -1)
            throw new RuntimeException("sendToVirtualShard() is for nodes");
        ((MyNetwork) network).sendToVirtualShard(id, shard, receivingAction, data, size);
    }

    public void sendToVirtualShardLeader(int shard, EventHandler receivingAction, EventParam data, int size)
    {
        if(this.id == -1)
            throw new RuntimeException("sendToVirtualShardLeader() is for nodes");
        ((MyNetwork) network).sendToVirtualShardLeader(id, shard, receivingAction, data, size);
    }

    public void sendToActualShard(int shard, EventHandler receivingAction, EventParam data, int size)
    {
        if(this.id == -1)
            throw new RuntimeException("sendToActualShard() is for nodes");
        ((MyNetwork) network).sendToActualShard(id, shard, receivingAction, data, size);
    }

    public int sendToTreeSons(EventHandler receivingAction, EventParam data, int size) {
        if(this.id == -1)
            throw new RuntimeException("sendToTreeSons() is for nodes");
        return ((MyNetwork) network).sendToTreeSons(id, receivingAction, data, size);
    }

    public int sendToTreeParent(EventHandler receivingAction, EventParam data, int size) {
        if(this.id == -1)
            throw new RuntimeException("sendToTreeParent() is for nodes");
        return ((MyNetwork) network).sendToTreeParent(id, receivingAction, data, size);
    }

    public double getNextIdleTime() {
        return nextIdleTime;
    }

    public void stayBusy(double busyTime, EventHandler nextAction, EventParam param) {
        nextIdleTime = EventDriver.getCurrentTime() + busyTime;
        totalBusyTime += busyTime;
        EventDriver.insertEvent(new Event(nextIdleTime, this, nextAction, param));
    }
}
