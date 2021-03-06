package edu.pku.infosec.node;

import edu.pku.infosec.event.EventDriver;
import edu.pku.infosec.event.NodeAction;

import static edu.pku.infosec.node.Network.EXTERNAL_ID;

public class Node {
    private final Network network;
    private final int id;
    private double nextIdleTime;
    private double totalBusyTime = 0;

    Node(int id, Network network) {
        this.id = id;
        this.network = network;
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
