package edu.pku.infosec.event;

import edu.pku.infosec.node.Network;
import edu.pku.infosec.node.Node;

import java.util.PriorityQueue;

public class EventDriver {
    private static final PriorityQueue<Event> eventQueue = new PriorityQueue<>();
    private static double currentTime;

    public static double getCurrentTime() {
        return currentTime;
    }

    public static void insertEvent(double timeToHappen, Node responsibleNode, NodeAction nodeAction) {
        eventQueue.add(new Event(timeToHappen, timeToHappen, responsibleNode, nodeAction));
    }

    public static void insertDelayedEvent(double timeToHappen, double originalTime, Node responsibleNode, NodeAction nodeAction) {
        eventQueue.add(new Event(timeToHappen, originalTime, responsibleNode, nodeAction));
    }

    public static void start() {
        while (!eventQueue.isEmpty()) {
            Event event = eventQueue.remove();
            currentTime = event.getTimeToHappen();
            event.happen();
        }
    }
}


class Event implements Comparable<Event> {
    private final double timeToHappen;
    private final double queueOrder;
    private final Node responsibleNode;
    private final NodeAction nodeAction;

    public Event(double timeToHappen, double queueOrder, Node responsibleNode, NodeAction nodeAction) {
        this.timeToHappen = timeToHappen;
        this.queueOrder = queueOrder;
        this.responsibleNode = responsibleNode;
        this.nodeAction = nodeAction;
    }

    public double getTimeToHappen() {
        return timeToHappen;
    }

    public void happen() {
        if (responsibleNode.getId() != Network.EXTERNAL_ID && responsibleNode.getNextIdleTime() > timeToHappen) {
            EventDriver.insertDelayedEvent(responsibleNode.getNextIdleTime(), queueOrder, responsibleNode, nodeAction);
        } else
            nodeAction.runOn(responsibleNode);
    }

    @Override
    public int compareTo(Event o) {
        if (timeToHappen == o.timeToHappen)
            return Double.compare(queueOrder, o.queueOrder);
        return Double.compare(timeToHappen, o.timeToHappen);
    }
}

