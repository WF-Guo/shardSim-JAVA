package edu.pku.infosec.customized;

import com.alibaba.fastjson.JSONObject;
import edu.pku.infosec.node.Network;

public class MyNetwork extends Network {
    public MyNetwork(int size, boolean limitBandwidth, int externalLatency, JSONObject modelConfig) {
        super(size, limitBandwidth, externalLatency);
        ModelData.nodeNum = size;
        // Use addEdge(u,v,latency,bandwidth) to create a directed connection between (u,v)
    }
}
