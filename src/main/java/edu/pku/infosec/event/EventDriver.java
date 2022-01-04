package edu.pku.infosec.event;

import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.Queue;

public class EventDriver {
    private static final Queue<Event> eventQueue = new PriorityQueue<>(Comparator.comparingLong(Event::getTimeToHappen));
    private static long currentTime;

    private static void nextEvent() {
        Event event = eventQueue.remove();
        currentTime = event.getTimeToHappen();
        event.happen();
    }

    public static long getCurrentTime() {
        return currentTime;
    }

    public static void insertEvent(Event event) {
        eventQueue.add(event);
    }
}
