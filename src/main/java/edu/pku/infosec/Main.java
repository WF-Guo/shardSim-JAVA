package edu.pku.infosec;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import edu.pku.infosec.customized.ModelData;
import edu.pku.infosec.customized.MyNetwork;
import edu.pku.infosec.event.EventDriver;
import edu.pku.infosec.node.Network;
import edu.pku.infosec.transaction.TxGenScheduler;
import edu.pku.infosec.transaction.TxInfo;
import edu.pku.infosec.transaction.TxInput;
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
        int nodeNum = Integer.parseInt(properties.getProperty("nodeNumber"));
        boolean limitBandwidth = Boolean.parseBoolean(properties.getProperty("limitBandwidth"));
        int externalLatency = Integer.parseInt(properties.getProperty("externalLatency"));
        JSONObject otherConfig = JSON.parseObject(properties.getProperty("model"));
        Network network = new MyNetwork(nodeNum, limitBandwidth, externalLatency, otherConfig);
        network.calcPath();
        // Initializing utxo set
        for(int i = 0; i < 10000; i++) {
            TxInfo coinbase = new TxInfo();
            coinbase.setOutputNum(1);
            TxStat.confirm(coinbase);
            ModelData.addInitUTXO(new TxInput(coinbase.id, 0));
        }
        TxGenScheduler.generate(network.externalNode, JSON.parseObject(properties.getProperty("transactions")));
        EventDriver.start();
        network.loadStat();
        System.out.println("Throughput:" + TxStat.throughput());
        System.out.println("Latency:" + TxStat.averageLatency());
        System.out.println("Overlap Consensus Num: " + ModelData.ConsensusCnt);
        System.out.println("False Overlap Consensus Num: " + ModelData.FalseConsensusCnt);
        System.out.println("Alert Rate: " + ModelData.FalseConsensusCnt * 1.0 / ModelData.ConsensusCnt);
    }
}
