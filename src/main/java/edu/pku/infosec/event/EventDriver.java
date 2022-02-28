package edu.pku.infosec.event;

import edu.pku.infosec.node.Node;

import java.util.PriorityQueue;

public class EventDriver {
    private static final PriorityQueue<Event> eventQueue = new PriorityQueue<>();
    private static double currentTime;

    public static double getCurrentTime() {
        return currentTime;
    }

    public static void insertEvent(double timeToHappen, Node responsibleNode, NodeAction nodeAction) {
        eventQueue.add(new Event(timeToHappen, responsibleNode, nodeAction));
    }

    public static void start() {
        while(!eventQueue.isEmpty()) {
            Event event = eventQueue.remove();
            currentTime = event.getTimeToHappen();
            event.happen();
        }
    }
}


class Event implements Comparable<Event> {
    private double timeToHappen;
    private final Node responsibleNode;
    private final NodeAction nodeAction;

    Event(double timeToHappen, Node responsibleNode, NodeAction nodeAction) {
        this.timeToHappen = timeToHappen;
        this.responsibleNode = responsibleNode;
        this.nodeAction = nodeAction;
    }

    public double getTimeToHappen() {
        return timeToHappen;
    }

    public void happen() {
        // client has an id of -1
        if(responsibleNode.getId() != -1 && responsibleNode.getNextIdleTime() > timeToHappen) {
            timeToHappen = responsibleNode.getNextIdleTime();
            EventDriver.insertEvent(responsibleNode.getNextIdleTime(), responsibleNode, nodeAction);
        }
        else
            nodeAction.runOn(responsibleNode);
    }

    @Override
    public int compareTo(Event o) {
        return Double.compare(timeToHappen, o.timeToHappen);
    }
}

