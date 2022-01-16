package edu.pku.infosec.node;

import edu.pku.infosec.event.Event;
import edu.pku.infosec.event.EventDriver;
import edu.pku.infosec.event.EventHandler;
import edu.pku.infosec.event.EventParam;

public class Node {
    private double nextIdleTime;
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

    public double getNextIdleTime() {
        return nextIdleTime;
    }

    public void stayBusy(double busyTime, EventHandler nextAction, EventParam param) {
        nextIdleTime = EventDriver.getCurrentTime() + busyTime;
        EventDriver.insertEvent(new Event(nextIdleTime, this, nextAction, param));
    }
}
