package edu.pku.infosec.node;

import edu.pku.infosec.event.EventDriver;
import edu.pku.infosec.event.NodeAction;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.PriorityQueue;

public abstract class Network {
    public static final int EXTERNAL_ID = -1;
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
        externalNode = new Node(EXTERNAL_ID, this);
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
        if (limitBandwidth)
            throw new RuntimeException("Bandwidth is required in this network.");
        addEdge(u, v, latency, 0);
    }

    protected final void addEdge(int u, int v, int latency, int bandwidth) {
        if (u < 0 || v < 0 || u >= nodes.length || v >= nodes.length)
            throw new RuntimeException("Node index out of bounds of length " + nodes.length);
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
            for (int i = 0; i < nodes.length; i++)
                if (dist[i][target] == Integer.MAX_VALUE)
                    throw new RuntimeException("Graph is unconnected!");
        }
    }

    final void sendMessage(int from, int to, NodeAction receivingAction, int size) {
        if (limitBandwidth) {
            new TransmitMessage(to, receivingAction, size).runOn(nodes[from]);
        } else {
            double receivingTime = EventDriver.getCurrentTime() + dist[from][to];
            EventDriver.insertEvent(receivingTime, nodes[to], receivingAction);
        }
    }

    final void sendIn(int nodeID, NodeAction receivingAction) {
        double receivingTime = EventDriver.getCurrentTime() + externalLatency;
        EventDriver.insertEvent(receivingTime, nodes[nodeID], receivingAction);
    }

    final void sendOut(NodeAction receivingAction) {
        double receivingTime = EventDriver.getCurrentTime() + externalLatency;
        EventDriver.insertEvent(receivingTime, externalNode, receivingAction);
    }

    public final List<Double> listNodeLoads() {
        List<Double> nodeLoads = new ArrayList<>();
        for (Node node : nodes) {
            nodeLoads.add(node.getTotalBusyTime());
        }
        return nodeLoads;
    }

    public class TransmitMessage implements NodeAction {
        private final int to;
        private final NodeAction receivingAction;
        private final int size;

        public TransmitMessage(int to, NodeAction receivingAction, int size) {
            this.to = to;
            this.receivingAction = receivingAction;
            this.size = size;
        }

        @Override
        public void runOn(Node currentNode) {
            if (currentNode.getId() == to)
                EventDriver.insertLocalAction(currentNode, receivingAction);
            else {
                Edge e = nextEdge[currentNode.getId()][to];
                if ((EventDriver.getCurrentTime() < e.nextIdleTime || !e.packetQueue.isEmpty()) &&
                        this != e.packetQueue.peek()) {
                    // wait for idle bandwidth
                    if (e.packetQueue.isEmpty())
                        EventDriver.insertEvent(e.nextIdleTime, currentNode, this);
                    e.packetQueue.add(this);
                } else {
                    if (this == e.packetQueue.peek())
                        e.packetQueue.remove();
                    e.nextIdleTime = EventDriver.getCurrentTime() + (double) size / e.bandwidth;
                    double receivingTime =
                            EventDriver.getCurrentTime() + e.latency + (double) size / e.bandwidth;
                    EventDriver.insertEvent(receivingTime, nodes[e.v], this); // Relay!
                    if (!e.packetQueue.isEmpty())
                        EventDriver.insertEvent(e.nextIdleTime, currentNode, e.packetQueue.peek());
                }
            }
        }
    }
}

class Edge {
    protected final int u;
    protected final int v;
    protected final int latency;
    protected final int bandwidth;
    protected final LinkedList<NodeAction> packetQueue;
    protected double nextIdleTime;

    public Edge(int u, int v, int latency, int bandwidth) {
        this.u = u;
        this.v = v;
        this.latency = latency;
        this.bandwidth = bandwidth;
        this.nextIdleTime = 0;
        packetQueue = new LinkedList<>();
    }
}
