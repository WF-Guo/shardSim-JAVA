package edu.pku.infosec.event;

import java.util.PriorityQueue;

public class EventDriver {
    private static final PriorityQueue<Event> eventQueue = new PriorityQueue<>();
    private static long currentTime;

    public static long getCurrentTime() {
        return currentTime;
    }

    public static void insertEvent(Event event) {
        eventQueue.add(event);
    }

    public static void start() {
        while(!eventQueue.isEmpty()) {
            Event event = eventQueue.remove();
            currentTime = event.getTimeToHappen();
            event.happen();
        }
    }
}
