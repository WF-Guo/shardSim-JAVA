package edu.pku.infosec.node;

import edu.pku.infosec.event.EventDriver;
import edu.pku.infosec.event.EventHandler;
import edu.pku.infosec.event.EventParam;

public class Node {
    private long nextIdleTime;
    private final Network network;
    private final int id;

    public Node(int id, Network network) {
        this.id = id;
        this.network = network;
    }

    public int getId() {
        return id;
    }

    public void sendMessage(int to, EventHandler receivingAction, EventParam data, int size) {
        network.sendMessage(id, to, receivingAction, data, size);
    }

    public long getNextIdleTime() {
        return nextIdleTime;
    }

    public void stayBusy(long busyTime) {
        nextIdleTime = EventDriver.getCurrentTime() + busyTime;
    }
}
