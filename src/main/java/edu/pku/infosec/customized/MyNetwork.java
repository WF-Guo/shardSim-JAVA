package edu.pku.infosec.customized;

import com.alibaba.fastjson.JSONObject;
import edu.pku.infosec.node.Network;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class MyNetwork extends Network {
    public MyNetwork(int size, boolean limitBandwidth, int externalLatency, JSONObject modelConfig) {
        super(size, limitBandwidth, externalLatency);
        Random random = new Random();
        ModelData.nodeNum = size;
        ModelData.shardNum = modelConfig.getInteger("shardNum");
        int maliciousNodeNum = modelConfig.getInteger("maliciousNodeNum");

        // Generate a random permutation
        List<Integer> permutationList = new ArrayList<>();
        Integer[] permutation = new Integer[size];
        for (int i = 0; i < size; i++)
            permutationList.add(i);
        Collections.shuffle(permutationList);
        permutationList.toArray(permutation);

        // Initialize shards
        int shardBeginIndex = 0;
        for (int shardId = 0; shardId < ModelData.shardNum; shardId++) {
            int shardSize = 0;
            // Let the first be leader
            int shardLeader = permutation[shardBeginIndex];
            ModelData.shard2Leader.put(shardId, shardLeader);
            // Put nodes into shards evenly
            for (int i = shardBeginIndex; i < size && i * ModelData.shardNum < size * (shardId + 1); i++) {
                ModelData.node2Shard.put(permutation[i], shardId);
                shardSize += 1;
            }
            ModelData.shardLeader2ShardSize.put(shardLeader, shardSize);

            // Group initialization
            int groupNum = (int) Math.sqrt(shardSize - 0.5);
            int groupBeginIndex = shardBeginIndex + 1; // skip shard leader
            for (int groupId = 0; groupId < groupNum; groupId++) {
                int groupSize = 1;
                int groupLeader = permutation[groupBeginIndex];
                ModelData.shardLeader2GroupLeaders.put(shardLeader, groupLeader);
                ModelData.groupLeader2ShardLeader.put(groupLeader, shardLeader);
                for (int i = groupBeginIndex + 1; i < shardBeginIndex + shardSize &&
                        (i - shardBeginIndex - 1) * groupNum < (shardSize - 1) * (groupId + 1); i++) {
                    ModelData.groupLeader2Members.put(groupLeader, permutation[i]);
                    ModelData.node2GroupLeader.put(permutation[i], groupLeader);
                    groupSize++;
                }
                ModelData.groupLeader2GroupSize.put(groupLeader, groupSize);
                groupBeginIndex += groupSize;
            }
            shardBeginIndex += shardSize;
        }

        // Malicious nodes
        Collections.shuffle(permutationList);
        ModelData.maliciousNodes.addAll(permutationList.subList(0, maliciousNodeNum));

        // Create random connection
        for (int i = 0; i < size; i++)
            for (int j = 0; j < size; j++)
                if (ModelData.node2Shard.get(i).equals(ModelData.node2Shard.get(j))
                        || random.nextDouble() <= 0.005) {
                    addEdge(i, j, 100, 20 * 1024 * 1024 / 1000);
                    addEdge(j, i, 100, 20 * 1024 * 1024 / 1000);
                }

    }
}
