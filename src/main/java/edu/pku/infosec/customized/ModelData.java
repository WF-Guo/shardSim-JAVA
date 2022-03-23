package edu.pku.infosec.customized;

import edu.pku.infosec.transaction.TxInfo;
import edu.pku.infosec.transaction.TxInput;
import edu.pku.infosec.util.GroupedList;
import edu.pku.infosec.util.GroupedSet;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ModelData {
    // Shard Structure
    public static final Map<Integer, Integer> node2Shard = new HashMap<>();
    public static final Map<Integer, Integer> shard2Leader = new HashMap<>();
    public static final GroupedList<Integer, Integer> shardLeader2GroupLeaders = new GroupedList<>();
    public static final Map<Integer, Integer> groupLeader2ShardLeader = new HashMap<>();
    public static final GroupedList<Integer, Integer> groupLeader2Members = new GroupedList<>();
    public static final Map<Integer, Integer> node2GroupLeader = new HashMap<>();
    public static final Map<Integer, Integer> shardLeader2ShardSize = new HashMap<>();
    public static final Map<Integer, Integer> groupLeader2GroupSize = new HashMap<>();
    public static final Set<Integer> maliciousNodes = new HashSet<>();
    // Transaction Processing
    public static final Map<TxInfo, Integer> ClientAccessPoint = new HashMap<>();
    public static final GroupedSet<TxInfo, Integer> ISSet = new GroupedSet<>();
    public static final GroupedSet<TxInfo, Integer> OSSet = new GroupedSet<>();
    public static final GroupedSet<TxInfo, Integer> RejectingISs = new GroupedSet<>();
    public static final GroupedSet<Integer, TxInput> utxoSetOnNode = new GroupedSet<>();
    public static final GroupedSet<Integer, TxInput> uncommittedInputsOnNode = new GroupedSet<>();
    private static final Map<Integer, Map<TxInfo, NodeSigningState>> txProc_base = new HashMap<>();
    // Constant
    public static int nodeNum;
    public static int shardNum;
    public static double verificationTimePerInput;
    public static double commitTimePerOutput;
    public static double clientSigVerificationTime;
    public static double commitTime;

    public static int getShardId(TxInput input) {
        int inputHash = input.hashCode();
        if (inputHash < 0)
            inputHash -= Integer.MIN_VALUE;
        return inputHash % shardNum;
    }

    public static NodeSigningState getState(int nodeId, TxInfo tx) {
        txProc_base.putIfAbsent(nodeId, new HashMap<>());
        txProc_base.get(nodeId).putIfAbsent(tx, new NodeSigningState());
        return txProc_base.get(nodeId).get(tx);
    }

    public static void clearState(int nodeId, TxInfo tx) {
        txProc_base.get(nodeId).remove(tx);
    }


    // addInitUTXO will be called at system initialization
    public static void addInitUTXO(TxInput utxo) {
        int shardLeader = shard2Leader.get(getShardId(utxo));
        utxoSetOnNode.getGroup(shardLeader).add(utxo);
        for (Integer groupLeader : shardLeader2GroupLeaders.getGroup(shardLeader)) {
            utxoSetOnNode.getGroup(groupLeader).add(utxo);
            for (Integer member : groupLeader2Members.getGroup(groupLeader)) {
                utxoSetOnNode.getGroup(member).add(utxo);
            }
        }
    }
}