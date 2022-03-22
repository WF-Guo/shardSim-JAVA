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
import java.util.List;
import java.util.Properties;

public class Main {
    public static void main(String[] args) {
        if (args.length != 1) {
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
        for (int i = 0; i < 10000; i++) {
            TxInfo coinbase = new TxInfo();
            coinbase.setOutputNum(1);
            TxStat.confirm(coinbase);
            ModelData.addInitUTXO(new TxInput(coinbase.id, 0));
        }
        TxGenScheduler.generate(network.externalNode, JSON.parseObject(properties.getProperty("transactions")));
        EventDriver.start();
        System.out.println("Throughput:" + TxStat.throughput());
        System.out.println("Latency:" + TxStat.averageLatency());
        final List<Double> loads = network.listNodeLoads();
        double max = 0, min = Long.MAX_VALUE, total = 0;
        for (double load : loads) {
            total += load;
            max = Math.max(max, load);
            min = Math.min(min, load);
        }
        double average = total / nodeNum, variance = 0;
        System.out.println("Range: " + (max - min));
        for (double load : loads) {
            variance += Math.pow(load - average, 2.0);
        }
        System.out.println("Variance: " + variance / nodeNum);

    }
}
