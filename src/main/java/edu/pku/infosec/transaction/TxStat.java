package edu.pku.infosec.transaction;

import edu.pku.infosec.event.EventDriver;

import java.util.HashMap;
import java.util.PriorityQueue;
import java.util.Random;

public class TxStat {
    private static final HashMap<Long, Long> submitTime = new HashMap<>();
    private static final HashMap<Long, Long> commitTime = new HashMap<>();
    private static final PriorityQueue<ShuffledTxInput> utxoSet = new PriorityQueue<>();

    public static void submit(TxInfo tx) {
        submitTime.put(tx.id, EventDriver.getCurrentTime());
    }

    public static void commit(TxInfo tx) {
        commitTime.put(tx.id, EventDriver.getCurrentTime());
        for (int i = 0; i < tx.outputNum; i++) {
            utxoSet.add(new ShuffledTxInput(new TxInput(tx.id, i)));
        }
    }

    public static TxInput getRandomUTXO() {
        return utxoSet.remove().input;
    }

    public static double averageLatency() {
        long n = 0;
        long latencySum = 0;
        for (long tx : submitTime.keySet()) {
            if (commitTime.containsKey(tx)) {
                latencySum += commitTime.get(tx);
                n++;
            }
        }
        return latencySum / n;
    }

    public static double throughput() {
        long n = commitTime.size();
        long time = EventDriver.getCurrentTime();
        return time / n;
    }

}

class ShuffledTxInput implements Comparable<ShuffledTxInput> {
    private static final Random random = new Random();
    private final int weight;
    TxInput input;

    public ShuffledTxInput(TxInput input) {
        this.weight = random.nextInt();
        this.input = input;
    }

    @Override
    public int compareTo(ShuffledTxInput o) {
        return Integer.compare(weight, o.weight);
    }
}