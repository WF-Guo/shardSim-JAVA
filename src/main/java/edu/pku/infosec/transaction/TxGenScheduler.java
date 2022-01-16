package edu.pku.infosec.transaction;

import com.alibaba.fastjson.JSONObject;
import edu.pku.infosec.customized.TxGenerator;
import edu.pku.infosec.event.Event;
import edu.pku.infosec.event.EventDriver;
import edu.pku.infosec.event.VoidEventParam;
import edu.pku.infosec.node.Node;

public class TxGenScheduler {
    public static void generate(Node client, JSONObject txConfig) {
        int number = txConfig.getInteger("number");
        int interval = txConfig.getInteger("interval");
        TxGenerator generator = new TxGenerator(txConfig);
        long time = 0;
        for (int i = 0; i < number; i++) {
            EventDriver.insertEvent(new Event(time, client, generator, new VoidEventParam()));
            time += interval;
        }
    }
}
