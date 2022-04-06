package edu.pku.infosec.customized;

import edu.pku.infosec.node.Node;
import edu.pku.infosec.transaction.TxInfo;
import edu.pku.infosec.transaction.TxInput;
import edu.pku.infosec.util.Counter;
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
    public static final Set<TxInput> utxoSet = new HashSet<>();
    public static final Map<TxInfo, Integer> ClientAccessPoint = new HashMap<>();
    public static final GroupedSet<TxInfo, Integer> ISSet = new GroupedSet<>();
    public static final GroupedSet<TxInfo, Integer> OSSet = new GroupedSet<>();
    public static final GroupedList<TxInfo, CollectivelySignedMessage> proofsOfAcceptance = new GroupedList<>();
    public static final GroupedList<TxInfo, CollectivelySignedMessage> proofsOfRejection = new GroupedList<>();
    public static final Counter<TxInfo> commitCounter = new Counter<>();
    public static final GroupedSet<Node, TxInput> node2SpendingSet = new GroupedSet<>();
    public static final Map<CollectivelySignedMessage, Block> prepareCoSi2Block = new HashMap<>();
    public static final Map<Node, Block> shardLeader2AssemblingBlock = new HashMap<>();
    private static final Map<Node, Map<Signable, NodeSigningState>> signingStateDS = new HashMap<>();
    // Constant
    public static int NODE_NUM;
    public static int SHARD_NUM;
    public static int BLOCK_SIZE_LIMIT;
    public static int BLOCK_TX_NUM_LIMIT;
    public static double UTXO_SELECT_TIME;
    public static double UTXO_REMOVE_TIME;
    public static double UTXO_INSERT_TIME;
    public static double BYTE_HASH_TIME;
    public static double ECDSA_POINT_MUL_TIME;
    public static double ECDSA_POINT_ADD_TIME;
    public static int INPUT_SIZE;
    public static int OUTPUT_SIZE;
    public static int TX_OVERHEAD_SIZE;
    public static int ECDSA_NUMBER_SIZE;
    public static int ECDSA_POINT_SIZE;
    public static int HASH_SIZE;

    public static int hashToShard(TxInput input) {
        int inputHash = input.hashCode();
        if (inputHash < 0)
            inputHash -= Integer.MIN_VALUE;
        return inputHash % SHARD_NUM;
    }

    // addInitUTXO will be called at system initialization
    public static void addInitUTXO(TxInput utxo) {
        utxoSet.add(utxo);
    }

    public static void clearClientState(TxInfo tx) {
        ClientAccessPoint.remove(tx);
        proofsOfAcceptance.removeGroup(tx);
        proofsOfRejection.removeGroup(tx);
        ISSet.removeGroup(tx);
        OSSet.removeGroup(tx);
        ClientAccessPoint.remove(tx);
        commitCounter.count(tx);
    }

    public static NodeSigningState getState(Node node, Signable message) {
        signingStateDS.putIfAbsent(node, new HashMap<>());
        final Map<Signable, NodeSigningState> signingStateMap = signingStateDS.get(node);
        signingStateMap.putIfAbsent(message, new NodeSigningState());
        return signingStateMap.get(message);
    }

    public static void clearState(Node node, Signable message) {
        final Map<Signable, NodeSigningState> signingStateMap = signingStateDS.get(node);
        signingStateMap.remove(message);
    }
}