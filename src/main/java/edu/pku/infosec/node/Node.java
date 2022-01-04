package edu.pku.infosec.node;

import edu.pku.infosec.event.EventDriver;

public class Node {
    private long nextIdleTime;

    public long getNextIdleTime() {
        return nextIdleTime;
    }

    public void stayBusy(long busyTime) {
        nextIdleTime = EventDriver.getCurrentTime() + busyTime;
    }
}
