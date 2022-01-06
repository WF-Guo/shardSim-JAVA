package edu.pku.infosec.node;

import edu.pku.infosec.event.EventDriver;

public class Node {
    private long nextIdleTime;
    private final int id;

    public Node(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    public long getNextIdleTime() {
        return nextIdleTime;
    }

    public void stayBusy(long busyTime) {
        nextIdleTime = EventDriver.getCurrentTime() + busyTime;
    }
}
