package edu.pku.infosec.node;

import edu.pku.infosec.event.EventDriver;
import edu.pku.infosec.event.NodeAction;

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

    public void sendMessage(int to, NodeAction receivingAction, int size) {
        if(id == Network.EXTERNAL_ID || to == Network.EXTERNAL_ID)
            throw new RuntimeException("sendMessage() is for nodes");
        network.sendMessage(id, to, receivingAction, size);
    }

    public void sendOut(NodeAction receivingAction) {
        network.sendOut(receivingAction);
    }

    public void sendIn(int id, NodeAction receivingAction) {
        if(this.id != Network.EXTERNAL_ID)
            throw new RuntimeException("sendIn() is for client");
        network.sendIn(id, receivingAction);
    }

    public double getNextIdleTime() {
        return nextIdleTime;
    }

    public void stayBusy(double busyTime, NodeAction nextAction) {
        if(nextIdleTime > EventDriver.getCurrentTime())
            throw new RuntimeException("Calling stayBusy more than once in one function");
        nextIdleTime = EventDriver.getCurrentTime() + busyTime;
        EventDriver.insertEvent(nextIdleTime, this, nextAction);
    }
}
