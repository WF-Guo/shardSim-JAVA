package edu.pku.infosec.node;

import edu.pku.infosec.event.Event;
import edu.pku.infosec.event.EventDriver;
import edu.pku.infosec.event.EventHandler;
import edu.pku.infosec.event.EventParam;

public class Node {
    private long nextIdleTime;
    private final Network network;
    private final int id;

    Node(int id, Network network) {
        this.id = id;
        this.network = network;
    }

    public int getId() {
        return id;
    }

    public void sendMessage(int to, EventHandler receivingAction, EventParam data, int size) {
        network.sendMessage(id, to, receivingAction, data, size);
    }

    public void sendOut(EventHandler receivingAction, EventParam data) {
        network.sendOut(receivingAction, data);
    }

    public void sendIn(int id, EventHandler receivingAction, EventParam data) {
        assert id == -1: "sendIn() is for client";
        network.sendIn(id,receivingAction, data);
    }

    public long getNextIdleTime() {
        return nextIdleTime;
    }

    public void stayBusy(long busyTime, EventHandler nextAction, EventParam param) {
        nextIdleTime = EventDriver.getCurrentTime() + busyTime;
        EventDriver.insertEvent(new Event(nextIdleTime, this, nextAction, param));
    }
}
