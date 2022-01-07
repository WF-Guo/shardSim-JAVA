package edu.pku.infosec.customized;

import edu.pku.infosec.node.Network;

public class MyNetwork extends Network {
    public MyNetwork(int size, boolean limitBandwidth) {
        super(size, limitBandwidth);
    }

    @Override
    public void configConnection() {
        // Use addEdge(u,v,latency,bandwidth) to create a directed connection between (u,v)
    }
}
