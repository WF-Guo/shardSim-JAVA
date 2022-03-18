package edu.pku.infosec.transaction;

import edu.pku.infosec.event.EventDriver;
import edu.pku.infosec.util.GroupedSet;
import edu.pku.infosec.util.RandomQueue;

import java.util.HashMap;

public class TxStat {
    private static final HashMap<Long, Double> submitTime = new HashMap<>();
    private static final HashMap<Long, Double> commitTime = new HashMap<>();
    private static final RandomQueue<TxInput> utxoSet = new RandomQueue<>();
    private static final GroupedSet<TxInput, TxInfo> relatedTxs = new GroupedSet<>();

    public static int utxoSize() {
        return utxoSet.size();
    }

    public static void submit(TxInfo tx) {
        submitTime.put(tx.id, EventDriver.getCurrentTime());
        for(TxInput input: tx.inputs)
        relatedTxs.put(input, tx);
    }

    public static void confirm(TxInfo tx) {
        if(commitTime.containsKey(tx.id))
            return; // Repeated
        if(tx.inputs.size() > 0) // not coinbase
            commitTime.put(tx.id, EventDriver.getCurrentTime());
        for (TxInput output: tx.outputs) {
            utxoSet.add(output);
        }
        for(TxInput spent: tx.inputs) {
            if(relatedTxs.getGroup(spent).isEmpty())
                throw new RuntimeException("Conflicting transactions are both committed.");
            for(TxInfo conflict: relatedTxs.getGroup(spent)) {
                if(conflict.equals(tx))
                    continue;
                for(TxInput released: conflict.inputs) {
                    if(released.equals(spent))
                        continue;
                    relatedTxs.getGroup(released).remove(conflict);
                    if(relatedTxs.getGroup(released).isEmpty()) {
                        utxoSet.add(released);
                    }
                }
            }
        }
        for(TxInput spent: tx.inputs)
            relatedTxs.removeGroup(spent);
    }

    public static TxInput getRandomUTXO() {
        return utxoSet.remove();
    }

    public static double averageLatency() {
        long n = 0;
        double latencySum = 0;
        for (long tx : submitTime.keySet()) {
            if (commitTime.containsKey(tx)) {
                latencySum += commitTime.get(tx);
                latencySum -= submitTime.get(tx);
                n++;
            }
        }
        return latencySum / n;
    }

    public static double throughput() {
        long n = commitTime.size();
        double time = EventDriver.getCurrentTime();
        return n / time;
    }

}