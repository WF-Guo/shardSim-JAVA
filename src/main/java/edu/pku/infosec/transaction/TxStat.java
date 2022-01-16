package edu.pku.infosec.transaction;

import edu.pku.infosec.event.EventDriver;
import edu.pku.infosec.util.RandomQueue;

import java.util.HashMap;

public class TxStat {
    private static final HashMap<Long, Double> submitTime = new HashMap<>();
    private static final HashMap<Long, Double> commitTime = new HashMap<>();
    private static final RandomQueue<TxInput> utxoSet = new RandomQueue<>();
    private static final HashMap<Long, TxInfo> conflictingTx = new HashMap<>();

    public static void markConflict(TxInfo tx1, TxInfo tx2) {
        conflictingTx.put(tx1.id, tx2);
        conflictingTx.put(tx2.id, tx1);
    }

    public static int utxoSize() {
        return utxoSet.size();
    }

    public static void submit(TxInfo tx) {
        submitTime.put(tx.id, EventDriver.getCurrentTime());
    }

    public static void commit(TxInfo tx) {
        if(commitTime.containsKey(tx.id))
            return; // Repeated
        if(tx.inputs.size() > 0) // not coinbase
            commitTime.put(tx.id, EventDriver.getCurrentTime());
        for (int i = 0; i < tx.outputNum; i++) {
            utxoSet.add(new TxInput(tx.id, i));
        }
        if(conflictingTx.containsKey(tx.id)) {
            TxInfo attack = conflictingTx.get(tx.id);
            if(commitTime.containsKey(attack.id))
                throw new RuntimeException("Conflicting transactions are both committed.");
            for (TxInput input: attack.inputs)
                if(!tx.inputs.contains(input))
                    utxoSet.add(input);
        }
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