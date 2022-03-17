package edu.pku.infosec.customized;

import com.alibaba.fastjson.JSONObject;
import edu.pku.infosec.event.EventHandler;
import edu.pku.infosec.event.EventParam;
import edu.pku.infosec.node.Network;

import java.util.*;

public class MyNetwork extends Network {
    final public int shardNum;
    // for overlapping shard (i,j), i < j always stands
    final public Map<shardPair, List<Integer>> overlapShards;
    final public Map<Integer, shardPair> originalShardIndex;

    public MyNetwork(int size, boolean limitBandwidth, int externalLatency, JSONObject modelConfig) {
        super(size, limitBandwidth, externalLatency);
        ModelData.nodeNum = size;
        Random random = new Random(1453);
        // Use addEdge(u,v,latency,bandwidth) to create a directed connection between (u,v)
        shardNum = modelConfig.getInteger("shardNum");
        ModelData.verificationTime = modelConfig.getDouble("VerificationTime");
        ModelData.maliciousNum = modelConfig.getInteger("MaliciousNum");
        ModelData.shardNum = shardNum;
        // generate the random permutation, where each segment of around 2N/m(m+1) nodes is an overlapping shard
        List<Integer> permutation = new ArrayList<>();
        for (int i = 0; i < size; ++i)
            permutation.add(i);
        Collections.shuffle(permutation);
        int segment = 2 * size / (shardNum * (shardNum + 1)), pos = 0;
        overlapShards = new HashMap<>();
        originalShardIndex = new HashMap<>();
        for (int i = 0; i < shardNum; ++i) {
            for (int j = i; j < shardNum; ++j) {
                List<Integer> nodeList = new ArrayList<>();
                for (int k = pos; (k < pos + segment) && (k < size); ++k) {
                    nodeList.add(permutation.get(k));
                    originalShardIndex.put(permutation.get(k), new shardPair(i, j));
                }
                // add edges, by default, bandwidth is 20Mbps, all edges have a latency of 100ms
                for (int k = pos; (k < pos + segment) && (k < size); ++k)
                    for (int l = k + 1; l < size; ++l)
                    {
                        if (l < pos + segment)
                        {
                            addEdge(permutation.get(k), permutation.get(l), 100, 20972);
                            addEdge(permutation.get(l), permutation.get(k), 100, 20972);
                        }
                        else if (random.nextDouble() <= 0.005) // on average each node has around 20 links
                        {
                            addEdge(permutation.get(k), permutation.get(l), 100, 20972);
                            addEdge(permutation.get(l), permutation.get(k), 100, 20972);
                        }
                    }
                pos += segment;
                overlapShards.put(new shardPair(i, j), nodeList);
            }
        }
        ModelData.originalShardIndex = originalShardIndex;
        ModelData.overlapShards = overlapShards;
    }

    final public void sendToOverlapShard(int from, int firstshard, int secondshard, EventHandler receivingAction,
                                  EventParam data, int size)
    {
        shardPair shards = new shardPair(firstshard, secondshard);
        List<Integer> nodes = overlapShards.get(shards);
        for (int node : nodes) {
            sendMessage(from, node, receivingAction, data, size);
        }
    }

    final public void sendToSelfOverlapLeader(int from, EventHandler receivingAction, EventParam data, int size)
    {
        shardPair shards = originalShardIndex.get(from);
        List<Integer> nodes = overlapShards.get(shards);
        sendMessage(from, nodes.get(0), receivingAction, data, size);
    }

    final public void sendToOverlapLeader(int from, int firstshard, int secondshard,
                                          EventHandler receivingAction, EventParam data, int size)
    {
        shardPair shards = new shardPair(firstshard, secondshard);
        List<Integer> nodes = overlapShards.get(shards);
        sendMessage(from, nodes.get(0), receivingAction, data, size);
    }

    // hash is necessary so that for a same transaction, a same set of nodes can be selected
    final public void sendToHalfOriginalShard
            (int from, int originalShard, int hash, EventHandler receivingAction, EventParam data, int size)
    {
        for (int i = 0; i < shardNum; ++i) {
            List<Integer> nodes = overlapShards.get(new shardPair(originalShard, i));
            for (int node : nodes) {
                if ((Objects.hash(node, hash)) % 2 == 0)
                    sendMessage(from, node, receivingAction, data, size);
            }
        }
    }

    final public int sendToTreeSons(int from, EventHandler receivingAction, EventParam data, int size)
    {
        List<Integer> nodes = overlapShards.get(originalShardIndex.get(from));
        int index = nodes.indexOf(from) + 1;
        int sendCnt = 0;
        if (nodes.size() > index * 2 - 1) {
            sendMessage(from, nodes.get(index * 2 - 1), receivingAction, data, size);
            sendCnt++;
        }
        if (nodes.size() > index * 2) {
            sendMessage(from, nodes.get(index * 2), receivingAction, data, size);
            sendCnt++;
        }
        return sendCnt;
    }

    final public int sendToTreeParent(int from, EventHandler receivingAction, EventParam data, int size)
    {
        List<Integer> nodes = overlapShards.get(originalShardIndex.get(from));
        int index = nodes.indexOf(from);
        if (index != 0) {
            sendMessage(from, nodes.get((index - 1) / 2), receivingAction, data, size);
            return 1;
        }
        else
            return 0;
    }

    final public void sendToOriginalShard
            (int from, int originalShard, EventHandler receivingAction, EventParam data, int size)
    {
        for (int i = 0; i < shardNum; ++i) {
            List<Integer> nodes = overlapShards.get(new shardPair(originalShard, i));
            for (int node : nodes) {
                sendMessage(from, node, receivingAction, data, size);
            }
        }
    }
}

class shardPair {
    final int first;
    final int second;
    public shardPair(int first, int second) {
        int f = Math.min(first, second);
        int s = Math.max(first, second);
        this.first = f;
        this.second = s;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        shardPair shardPair = (shardPair) o;
        return first == shardPair.first && second == shardPair.second;
    }

    @Override
    public int hashCode() {
        return Objects.hash(first, second);
    }
}