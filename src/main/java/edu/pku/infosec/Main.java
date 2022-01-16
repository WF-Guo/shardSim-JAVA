package edu.pku.infosec;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import edu.pku.infosec.customized.MyNetwork;
import edu.pku.infosec.event.EventDriver;
import edu.pku.infosec.node.Network;
import edu.pku.infosec.transaction.TxGenScheduler;
import edu.pku.infosec.transaction.TxStat;

import java.io.FileReader;
import java.io.IOException;
import java.util.Properties;

public class Main {
    public static void main(String[] args) {
        if(args.length != 1) {
            System.err.println("Usage: java --Xms32000m -Xmx48000m -jar shardSim.jar <ConfigFile>");
            return;
        }
        Properties properties = new Properties();
        try {
            properties.load(new FileReader(args[0]));
        } catch (IOException ioException) {
            System.err.println("Fail to load config");
            return;
        }
        int nodeNum = Integer.getInteger(properties.getProperty("nodeNumber"));
        boolean limitBandwidth = Boolean.getBoolean(properties.getProperty("limitBandwidth"));
        int externalLatency = Integer.getInteger(properties.getProperty("externalLatency"));
        JSONObject otherConfig = JSON.parseObject(properties.getProperty("model"));
        Network network = new MyNetwork(nodeNum, limitBandwidth, externalLatency, otherConfig);
        network.calcPath();
        TxGenScheduler.generate(network.externalNode, JSON.parseObject(properties.getProperty("transactions")));
        EventDriver.start();
        System.out.println("Throughput:" + TxStat.throughput());
        System.out.println("Latency:" + TxStat.averageLatency());
    }
}
