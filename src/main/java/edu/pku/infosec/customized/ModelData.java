package edu.pku.infosec.customized;

import edu.pku.infosec.transaction.TxInput;

import java.util.*;

public class ModelData {
    public static int nodeNum;
    public static int shardNum;
    public static double verificationTime;
    public static int maliciousNum;
    public static long ConsensusCnt = 0;
    public static long FalseConsensusCnt = 0;
    public static Map<shardPair, List<Integer>> overlapShards;
    public static Map<Integer, shardPair> originalShardIndex;
    public final static Map<Long, List<TxInput>> collectedVerification = new HashMap<>();
    private final static Set<TxInput> UTXO = new HashSet<>();
    private final static Map<TxInput, Long> lockedUTXO = new HashMap<>();

    //TODO: every honest validator shares the same UTXO, should we set an independent UTXO set for different validators?

    // addInitUTXO will be called at system initialization
    public static void addInitUTXO(TxInput utxo) {
        UTXO.add(utxo);
    }

    public static void addUTXO(TxInput utxo) {
        UTXO.add(utxo);
    }

    public static boolean verifyUTXO(TxInput input, long tid) {
        // return true iff the utxo exists and is not locked for another transaction
        if (!UTXO.contains(input))
            return false;
        if (!lockedUTXO.containsKey(input)) {
            lockedUTXO.put(input, tid); // locked if verification passed, in case conflicting transctions come
            return true;
        }
        return lockedUTXO.get(input) == tid;
    }

    public static void useUTXO(TxInput utxo) {
        UTXO.remove(utxo);
        lockedUTXO.remove(utxo);
    }

    public static void unlockUTXO(TxInput utxo, long tid) {
        if (lockedUTXO.get(utxo) == tid)
            lockedUTXO.remove(utxo);
    }

}
