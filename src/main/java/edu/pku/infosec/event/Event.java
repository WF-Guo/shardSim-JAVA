package edu.pku.infosec.event;

import edu.pku.infosec.node.Node;

public class Event {
    private long timeToHappen;
    private final Node responsibleNode;
    private final EventHandler handlerFunc;
    private final EventParam params;

    public Event(long timeToHappen, Node responsibleNode, EventHandler handlerFunc, EventParam params) {
        this.timeToHappen = timeToHappen;
        this.responsibleNode = responsibleNode;
        this.handlerFunc = handlerFunc;
        this.params = params;
    }

    public long getTimeToHappen() {
        return timeToHappen;
    }

    public void happen() {
        if(responsibleNode.getNextIdleTime() > timeToHappen) {
            timeToHappen = responsibleNode.getNextIdleTime();
            EventDriver.insertEvent(this);
        }
        else
            handlerFunc.run(responsibleNode, params);
    }
}
