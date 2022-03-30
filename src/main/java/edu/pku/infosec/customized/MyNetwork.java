package edu.pku.infosec.customized;

import com.alibaba.fastjson.JSONObject;
import edu.pku.infosec.event.NodeAction;
import edu.pku.infosec.node.Network;

import java.util.*;

public class MyNetwork extends Network {
    final public int shardNum;

    final public Map<Integer, List<Integer>> virtualShards; // member of each virtual shard
    final public Map<Integer, List<Integer>> actualShards; // member of each actual shard
    final public Map<Integer, Set<Integer>> virtualShardContainList; // actual shards each virtual shard contains
    final public Map<Integer, Integer> treeParent;
    final public Map<Integer, List<Integer>> treeChildren;
    final public Map<Integer, Integer> virtualShardIndex;

    public MyNetwork(int size, boolean limitBandwidth, int externalLatency, JSONObject modelConfig) {
        super(size, limitBandwidth, externalLatency);
        ModelData.nodeNum = size;
        Random random = new Random(1453);
        // Use addEdge(u,v,latency,bandwidth) to create a directed connection between (u,v)
        shardNum = modelConfig.getInteger("shardNum"); // for overlapshard, shardNum is actual shard number (2^x)
        ModelData.verificationTime = modelConfig.getDouble("VerificationTime");
        ModelData.maliciousNum = modelConfig.getInteger("MaliciousNum");
        ModelData.shardNum = shardNum;

        ModelData.UTXORemoveTime = modelConfig.getDouble("UTXORemoveTime");
        ModelData.UTXOAddTime = modelConfig.getDouble("UTXOAddTime");
        ModelData.hashTimePerByte = modelConfig.getDouble("hashTimePerByte");
        ModelData.ECDSAPointMulTime = modelConfig.getDouble("ECDSAPointMulTime");
        ModelData.ECDSAPointAddTime = modelConfig.getDouble("ECDSAPointAddTime");
        ModelData.sizePerInput = modelConfig.getInteger("sizePerInput");
        ModelData.sizePerOutput = modelConfig.getInteger("sizePerOutput");
        ModelData.txOverhead = modelConfig.getInteger("txOverhead");
        ModelData.ECDSANumberSize = modelConfig.getInteger("ECDSANumberSize");
        ModelData.ECDSAPointSize = modelConfig.getInteger("ECDSAPointSize");
        ModelData.hashSize = modelConfig.getInteger("hashSize");
        // note: basically overlapshard map nodes according to their round hash, here we use permutation for simplicity
        // using permutation results in more balanced node distribution, but should be close to hash when nodes are many
        List<Integer> permutation = new ArrayList<>();
        for (int i = 0; i < size; ++i)
            permutation.add(i);
        Collections.shuffle(permutation);
        int segment = size / (shardNum - 1) + 1, pos = 0;
        virtualShards = new HashMap<>();
        actualShards = new HashMap<>();
        virtualShardContainList = new HashMap<>();
        treeParent = new HashMap<>();
        treeChildren = new HashMap<>();
        virtualShardIndex = new HashMap<>();
        Queue<Integer> shardIdQueue = new ArrayDeque<>();
        int shardId;
        for (shardId = 0; shardId < shardNum; ++shardId) {
            shardIdQueue.add(shardId);
            actualShards.put(shardId, new ArrayList<>());
        }
        while (shardIdQueue.size() > 1) {
            int fisrtChild, secondChild;
            fisrtChild = shardIdQueue.remove();
            secondChild = shardIdQueue.remove();
            treeParent.put(fisrtChild, shardId);
            treeParent.put(secondChild, shardId);
            treeChildren.put(shardId, new ArrayList<>());
            treeChildren.get(shardId).add(fisrtChild);
            treeChildren.get(shardId).add(secondChild);
            virtualShardContainList.put(shardId, new HashSet<>());

            List<Integer> nodeList = new ArrayList<>();
            for (int k = pos; (k < pos + segment) && (k < size); ++k) {
                nodeList.add(permutation.get(k));
                virtualShardIndex.put(permutation.get(k), shardId);
            }
            // add edges, by default, bandwidth is 20Mbps, all edges have a latency of 100ms
            for (int k = pos; (k < pos + segment) && (k < size); ++k) {
                for (int l = k + 1; l < size; ++l) {
                    if (l < pos + segment) {
                        addEdge(permutation.get(k), permutation.get(l), 100, 20972);
                        addEdge(permutation.get(l), permutation.get(k), 100, 20972);
                    } else if (random.nextDouble() <= 0.005) // on average each node has around 20 links
                    {
                        addEdge(permutation.get(k), permutation.get(l), 100, 20972);
                        addEdge(permutation.get(l), permutation.get(k), 100, 20972);
                    }
                }
            }
            pos += segment;
            Queue<Integer> ergodic = new ArrayDeque<>();
            ergodic.add(shardId);
            while (ergodic.size() > 0) {
                int shard = ergodic.remove();
                if (shard < shardNum) { // an actual shard
                    actualShards.get(shard).addAll(nodeList);
                    virtualShardContainList.get(shardId).add(shard);
                }
                else {
                    ergodic.addAll(treeChildren.get(shard));
                }
            }
            virtualShards.put(shardId, nodeList);
            shardIdQueue.add(shardId);
            shardId++;
        }
        ModelData.shardParent = treeParent;
        ModelData.virtualShardContainList = virtualShardContainList;
        ModelData.virtualShards = virtualShards;
        ModelData.virtualShardIndex = virtualShardIndex;
    }

    final public void sendToVirtualShard(int from, int shard, NodeAction receivingAction,
                                  int size)
    {
        List<Integer> nodes = virtualShards.get(shard);
        for (int node : nodes) {
            sendMessage(from, node, receivingAction, size);
        }
    }

    final public void sendToVirtualShardLeader(int from, int shard, NodeAction receivingAction,
                                               int size)
    {
        List<Integer> nodes = virtualShards.get(shard);
        sendMessage(from, nodes.get(0), receivingAction, size);
    }

    final public void sendToActualShard(int from, int shard, NodeAction receivingAction, int size)
    {
        List<Integer> nodes = actualShards.get(shard);
        for (int node : nodes) {
            sendMessage(from, node, receivingAction, size);
        }
    }

    final public int sendToTreeSons(int from, NodeAction receivingAction, int size)
    {
        List<Integer> nodes = virtualShards.get(virtualShardIndex.get(from));
        int index = nodes.indexOf(from) + 1;
        int sendCnt = 0;
        if (nodes.size() > index * 2 - 1) {
            sendMessage(from, nodes.get(index * 2 - 1), receivingAction, size);
            sendCnt++;
        }
        if (nodes.size() > index * 2) {
            sendMessage(from, nodes.get(index * 2), receivingAction, size);
            sendCnt++;
        }
        return sendCnt;
    }

    final public int sendToTreeParent(int from, NodeAction receivingAction, int size)
    {
        List<Integer> nodes = virtualShards.get(virtualShardIndex.get(from));
        int index = nodes.indexOf(from);
        if (index != 0) {
            sendMessage(from, nodes.get((index - 1) / 2), receivingAction, size);
            return 1;
        }
        else
            return 0;
    }
}