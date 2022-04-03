package edu.pku.infosec.event;

import edu.pku.infosec.node.Network;
import edu.pku.infosec.node.Node;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.PriorityQueue;

public class EventDriver {
    private static final PriorityQueue<Event> eventQueue = new PriorityQueue<>();
    private static double currentTime;

    public static double getCurrentTime() {
        return currentTime;
    }

    public static void insertEvent(double timeToHappen, Node responsibleNode, NodeAction nodeAction) {
        eventQueue.add(new Event(timeToHappen, responsibleNode, nodeAction, false));
    }

    protected static void insertDelayedEvent(double timeToHappen, Node responsibleNode, NodeAction nodeAction) {
        eventQueue.add(new Event(timeToHappen, responsibleNode, nodeAction, true));
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
    private final Node responsibleNode;
    private final NodeAction nodeAction;
    private static final HashMap<Node, LinkedList<NodeAction>> node2DelayedActions = new HashMap<>();
    private final boolean delayed;

    public Event(double timeToHappen, Node responsibleNode, NodeAction nodeAction, boolean delayed) {
        this.timeToHappen = timeToHappen;
        this.responsibleNode = responsibleNode;
        this.nodeAction = nodeAction;
        this.delayed = delayed;
    }

    public double getTimeToHappen() {
        return timeToHappen;
    }

    public void happen() {
        node2DelayedActions.putIfAbsent(responsibleNode, new LinkedList<>());
        final LinkedList<NodeAction> delayedActions = node2DelayedActions.get(responsibleNode);

        if (responsibleNode.getId() != Network.EXTERNAL_ID && responsibleNode.getNextIdleTime() > timeToHappen) {
            delayedActions.add(nodeAction);
        } else {
            nodeAction.runOn(responsibleNode);
            if (!delayedActions.isEmpty())
                EventDriver.insertDelayedEvent(responsibleNode.getNextIdleTime(), responsibleNode, delayedActions.remove());
        }
    }

    @Override
    public int compareTo(Event o) {
        if (timeToHappen == o.timeToHappen)
            return -Boolean.compare(delayed, o.delayed);
        return Double.compare(timeToHappen, o.timeToHappen);
    }
}
