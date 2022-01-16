package edu.pku.infosec.node;

import edu.pku.infosec.event.Event;
import edu.pku.infosec.event.EventDriver;
import edu.pku.infosec.event.EventHandler;
import edu.pku.infosec.event.EventParam;

import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;

public abstract class Network {
    public final Node externalNode;
    private final Node[] nodes;
    private final List<List<Edge>> graph;
    private final int[][] dist;
    private final Edge[][] nextEdge;
    private final boolean limitBandwidth;
    private final long externalLatency;

    protected Network(int size, boolean limitBandwidth, long externalLatency) {
        this.limitBandwidth = limitBandwidth;
        this.externalLatency = externalLatency;
        nodes = new Node[size];
        externalNode = new Node(-1, this);
        for (int i = 0; i < nodes.length; i++) {
            nodes[i] = new Node(i, this);
        }
        graph = new ArrayList<>(size);
        dist = new int[size][size];
        nextEdge = new Edge[size][size];
        for (int i = 0; i < size; i++) {
            graph.add(new ArrayList<>());
            for (int j = 0; j < size; j++)
                dist[i][j] = Integer.MAX_VALUE;
        }
    }

    protected final void addEdge(int u, int v, int latency) {
        if(limitBandwidth)
            throw new RuntimeException("Bandwidth is required in this network.");
        graph.get(v).add(new Edge(u, v, latency, 0));
    }

    protected final void addEdge(int u, int v, int latency, int bandwidth) {
        graph.get(v).add(new Edge(u, v, latency, bandwidth));
    }

    public final void calcPath() {
        // Dijkstra
        class State implements Comparable<State> {
            final int nodeID;
            final int dist;

            public State(int nodeID, int dist) {
                this.nodeID = nodeID;
                this.dist = dist;
            }

            @Override
            public int compareTo(State s) {
                return Integer.compare(dist, s.dist);
            }

        }
        for (int target = 0; target < nodes.length; target++) {
            PriorityQueue<State> prq = new PriorityQueue<>();
            dist[target][target] = 0;
            prq.add(new State(target, 0));
            while (!prq.isEmpty()) {
                State state = prq.remove();
                int v = state.nodeID;
                if (state.dist > dist[v][target])
                    continue;
                for (Edge edge : graph.get(v)) {
                    int u = edge.u;
                    if (dist[v][target] + edge.latency < dist[u][target]) {
                        dist[u][target] = dist[v][target] + edge.latency;
                        if (limitBandwidth)
                            nextEdge[u][target] = edge;
                        prq.add(new State(u, dist[u][target]));
                    }
                }
            }
        }
    }

    final void sendMessage(int from, int to, EventHandler receivingAction, EventParam data, int size) {
        if (limitBandwidth) {
            EventDriver.insertEvent(
                    new Event(
                            EventDriver.getCurrentTime(),
                            nodes[from],
                            new EventHandler() {
                                @Override
                                public void run(Node currentNode, EventParam param) {
                                    MessageRelayParam messageRelayParam = (MessageRelayParam) param;
                                    if (currentNode.getId() == to)
                                        receivingAction.run(currentNode, data);
                                    else {
                                        Edge e = nextEdge[currentNode.getId()][to];
                                        if (EventDriver.getCurrentTime() < e.nextIdleTime) {
                                            // wait for idle bandwidth
                                            EventDriver.insertEvent(
                                                    new Event(e.nextIdleTime, currentNode, this, messageRelayParam)
                                            );
                                        } else {
                                            e.nextIdleTime = EventDriver.getCurrentTime() + (double) size / e.bandwidth;
                                            double receivingTime = EventDriver.getCurrentTime() + e.latency;
                                            EventDriver.insertEvent(new Event(receivingTime, nodes[e.v], this,
                                                    messageRelayParam)); // Relay!
                                        }
                                    }
                                }
                            },
                            new MessageRelayParam(receivingAction, data)
                    )
            );
        } else {
            double receivingTime = EventDriver.getCurrentTime() + dist[from][to];
            EventDriver.insertEvent(new Event(receivingTime, nodes[to], receivingAction, data));
        }
    }

    final void sendIn(int nodeID, EventHandler receivingAction, EventParam data) {
        double receivingTime = EventDriver.getCurrentTime() + externalLatency;
        EventDriver.insertEvent(new Event(receivingTime, nodes[nodeID], receivingAction, data));
    }

    final void sendOut(EventHandler receivingAction, EventParam data) {
        double receivingTime = EventDriver.getCurrentTime() + externalLatency;
        EventDriver.insertEvent(new Event(receivingTime, externalNode, receivingAction, data));
    }
}

class Edge {
    public final int u;
    public final int v;
    public final int latency;
    public final int bandwidth;
    public double nextIdleTime;

    public Edge(int u, int v, int latency, int bandwidth) {
        this.u = u;
        this.v = v;
        this.latency = latency;
        this.bandwidth = bandwidth;
        this.nextIdleTime = 0;
    }
}

class MessageRelayParam extends EventParam {
    EventHandler finalHandler;
    EventParam data;

    public MessageRelayParam(EventHandler finalHandler, EventParam data) {
        this.finalHandler = finalHandler;
        this.data = data;
    }
}