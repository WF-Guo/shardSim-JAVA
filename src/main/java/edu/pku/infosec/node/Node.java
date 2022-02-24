package edu.pku.infosec.node;

import edu.pku.infosec.customized.MyNetwork;
import edu.pku.infosec.event.Event;
import edu.pku.infosec.event.EventDriver;
import edu.pku.infosec.event.EventHandler;
import edu.pku.infosec.event.EventParam;

import java.util.HashMap;
import java.util.Map;

public class Node {
    private double nextIdleTime;
    private final Network network;
    private final int id;

    public final Map<Long, Integer> signatureCnt;
    public final Map<Long, Integer> passCnt;
    public final Map<Long, Integer> rollBackSignatureCnt;
    public final Map<Long, Integer> rollBackCnt;

    Node(int id, Network network) {
        this.id = id;
        this.network = network;
        signatureCnt = new HashMap<>();
        passCnt = new HashMap<>();
        rollBackCnt = new HashMap<>();
        rollBackSignatureCnt = new HashMap<>();
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

    public void sendToOverlapShard(int firstshard, int secondshard, EventHandler receivingAction,
                                  EventParam data, int size)
    {
        if(this.id != -1)
            throw new RuntimeException("sendToOverlapShard() is for client");
        ((MyNetwork) network).sendToOverlapShard(id, firstshard, secondshard, receivingAction, data, size);
    }

    public void sendToSelfOverlapLeader(EventHandler receivingAction, EventParam data, int size)
    {
        if(this.id != -1)
            throw new RuntimeException("sendToSelfOverlapLeader() is for client");
        ((MyNetwork) network).sendToSelfOverlapLeader(id, receivingAction, data, size);
    }

    public void sendToOverlapLeader(int firstshard, int secondshard, EventHandler receivingAction, EventParam data, int size)
    {
        if(this.id != -1)
            throw new RuntimeException("sendToOverlapLeader() is for client");
        ((MyNetwork) network).sendToOverlapLeader(id, firstshard, secondshard, receivingAction, data, size);
    }

    public void sendToOriginalShards(EventHandler receivingAction, EventParam data, int size)
    {
        if(this.id != -1)
            throw new RuntimeException("sendToOriginalShards() is for client");
        ((MyNetwork) network).sendToOriginalShards(id, receivingAction, data, size);
    }

    public void sendToHalfOriginalShard
            (int originalShard, int hash, EventHandler receivingAction, EventParam data, int size)
    {
        if(this.id != -1)
            throw new RuntimeException("sendToHalfOriginalShard() is for client");
        ((MyNetwork) network).sendToHalfOriginalShard(id, hash, originalShard, receivingAction, data, size);
    }

    public void sendToOriginalShard
            (int originalShard, EventHandler receivingAction, EventParam data, int size) {
        if(this.id != -1)
            throw new RuntimeException("sendToOriginalShard() is for client");
        ((MyNetwork) network).sendToOriginalShard(id, originalShard, receivingAction, data, size);
    }

    public double getNextIdleTime() {
        return nextIdleTime;
    }

    public void stayBusy(double busyTime, EventHandler nextAction, EventParam param) {
        nextIdleTime = EventDriver.getCurrentTime() + busyTime;
        EventDriver.insertEvent(new Event(nextIdleTime, this, nextAction, param));
    }
}
