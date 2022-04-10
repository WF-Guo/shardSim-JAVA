package edu.pku.infosec.event;

import edu.pku.infosec.node.Network;
import edu.pku.infosec.node.Node;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.PriorityQueue;

public class EventDriver {
    private static final PriorityQueue<Event> eventQueue = new PriorityQueue<>();
    private static double currentTime;
    private static final HashMap<Node, LinkedList<NodeAction>> node2DelayedActions = new HashMap<>();

    public static double getCurrentTime() {
        return currentTime;
    }

    public static void insertEvent(double timeToHappen, Node responsibleNode, NodeAction nodeAction) {
        eventQueue.add(new Event(timeToHappen, responsibleNode, nodeAction, false));
    }

    public static void insertLocalAction(Node currentNode, NodeAction nodeAction) {
        node2DelayedActions.putIfAbsent(currentNode, new LinkedList<>());
        node2DelayedActions.get(currentNode).add(nodeAction);
    }

    protected static void insertDelayedEvent(double timeToHappen, Node responsibleNode, NodeAction nodeAction) {
        eventQueue.add(new Event(timeToHappen, responsibleNode, nodeAction, true));
    }

    public static void start() {
        while (!eventQueue.isEmpty()) {
            final Event event = eventQueue.remove();
            final Node node = event.responsibleNode;
            final NodeAction action = event.nodeAction;
            node2DelayedActions.putIfAbsent(node, new LinkedList<>());
            final LinkedList<NodeAction> delayedActions = node2DelayedActions.get(node);
            currentTime = event.timeToHappen;
            if (action.getClass() == Network.TransmitMessage.class) {
                action.runOn(node);
            } else if (node.getId() != Network.EXTERNAL_ID && node.getNextIdleTime() > currentTime) {
                delayedActions.add(action);
            } else {
                action.runOn(node);
                if (!delayedActions.isEmpty())
                    EventDriver.insertDelayedEvent(node.getNextIdleTime(), node, delayedActions.remove());
            }
        }
    }
}

class Event implements Comparable<Event> {
    final double timeToHappen;
    final Node responsibleNode;
    final NodeAction nodeAction;
    final boolean delayed;

    public Event(double timeToHappen, Node responsibleNode, NodeAction nodeAction, boolean delayed) {
        this.timeToHappen = timeToHappen;
        this.responsibleNode = responsibleNode;
        this.nodeAction = nodeAction;
        this.delayed = delayed;
    }

    @Override
    public int compareTo(Event o) {
        if (timeToHappen == o.timeToHappen)
            return -Boolean.compare(delayed, o.delayed);
        return Double.compare(timeToHappen, o.timeToHappen);
    }
}
