package edu.pku.infosec.transaction;

import com.alibaba.fastjson.JSONObject;
import edu.pku.infosec.util.ProgressBarUpdater;
import edu.pku.infosec.customized.TxGeneration;
import edu.pku.infosec.event.EventDriver;
import edu.pku.infosec.node.Node;

import java.util.Timer;

public class TxGenScheduler {
    public static void generate(Node client, JSONObject txConfig) {
        int number = txConfig.getInteger("number");
        double interval = txConfig.getDouble("interval");
        TxGeneration generator = new TxGeneration(txConfig);
        double time = 0;
        for (int i = 0; i < number; i++) {
            EventDriver.insertEvent(time, client, generator);
            time += interval;
        }
        new Timer(true).scheduleAtFixedRate(new ProgressBarUpdater(number), 0, 500);
    }
}
