package edu.pku.infosec.customized;

import edu.pku.infosec.event.EventHandler;
import edu.pku.infosec.event.EventParam;
import edu.pku.infosec.node.Node;
import edu.pku.infosec.transaction.TxInfo;
import edu.pku.infosec.transaction.TxInput;
import edu.pku.infosec.transaction.TxStat;

import java.util.*;

/*
        Define your transaction processing rule here
        You can use the following to describe your model behaviour:
        1. currentNode.getId() returns the id of local node, which may be useful
        2. call TxStat.commit(tid) after it is safe for user to confirm that transaction
        3. Implement EventHandler and corresponding EventParam to define a new event type
        4. If you want local node to simulate a time-consuming task,
           call currentNode.stayBusy(time,eventHandler,eventParam) to lock the node and schedule what to do next.
           You can pack consecutive local tasks into a single one, until you need to send message to other nodes.
        5. To simulate sending message to another node, call currentNode.sendMessage(receiver,eventHandler,eventParam).
           It will calculate the transmission delay and let the receiving node run the eventHandler after the delay.
        6. To send message from node to external entities such as a client, call currentNode.sendOut.
           The eventHandler of this message will run on a special node with id=-1. You can use currentNode.sendIn
           to send message to a node on an external node, or use currentNode.stayBusy to delay that sending.
           Note that task conflict is ignored on an external node, so stayBusy won't get it really busy.
        7. If data structures is required for processing transactions, it is recommended that you define them as
           static fields in ModelData.
        8. If you need a coinbase tx, just create a transaction with no input and commit it, it will not be
           counted in throughput and latency
         */

public class TxProcessing implements EventHandler {
    @Override
    public void run(Node currentNode, EventParam param) {
        //the receiving node forwards the transaction
        TxInfo txinfo = (TxInfo) param;
        Set<Integer> shards = new HashSet<>();
        for (TxInput input : txinfo.inputs) {
            int responsibleShard = (int)input.tid % ModelData.shardNum;
            shards.add(responsibleShard);
        }
        int outputShard = (int)txinfo.id % ModelData.shardNum;
        shards.add(outputShard);

        Iterator<Integer> it = shards.iterator();
        int minContainShard = ModelData.shardParent.get(it.next());
        // minContainShard should be the smallest virtual shard containing all involved actual shard
        while (!ModelData.virtualShardContainList.get(minContainShard).containsAll(shards)) {
            minContainShard = ModelData.shardParent.get(minContainShard);
        }

        currentNode.sendToVirtualShardLeader(minContainShard, new PreprepareVerify(), param,
                        txinfo.inputs.size() * 48 + txinfo.outputNum * 8 + 20);
    }
}

class PreprepareVerify implements EventHandler {
    @Override
    public void run(Node currentNode, EventParam param) {
        TxInfo txinfo = (TxInfo) param;
        int verifyCnt = 0;
        if (currentNode.getId() >= ModelData.maliciousNum) {
            for (TxInput input : txinfo.inputs) {
                verifyCnt++;
                if (!ModelData.verifyUTXO(input, txinfo.id)) {
                    currentNode.verificationCnt.put(txinfo.id, 0);
                    currentNode.stayBusy(ModelData.verificationTime * verifyCnt, new PreprepareFoward(),param);
                    return;
                }
            }
        }
        currentNode.verificationCnt.put(txinfo.id, 1);
        currentNode.stayBusy(ModelData.verificationTime * verifyCnt, new PreprepareFoward(), param);
    }
}

class VerificationResult extends EventParam {
    public final TxInfo tx;
    public final int pass;

    public VerificationResult(int pass, TxInfo tx) {
        this.pass = pass;
        this.tx = tx;
    }
}

class PreprepareFoward implements EventHandler {
    @Override
    public void run(Node currentNode, EventParam param) {
        TxInfo txinfo = (TxInfo) param;
        int sons = currentNode.sendToTreeSons(new PreprepareVerify(), param, txinfo.inputs.size() * 48 +
                txinfo.outputNum * 8 + 20);
        if (sons == 0) { // already leaf
            currentNode.sendToTreeParent(new PreprepareReturn(), new VerificationResult(
                    currentNode.verificationCnt.get(txinfo.id), txinfo),
                    txinfo.inputs.size() * 48 + txinfo.outputNum * 8 + 21 + 64);
            currentNode.verificationCnt.remove(txinfo.id);
        }
        else
            currentNode.sonWaitCnt.put(txinfo.id, sons);
    }
}

class PreprepareReturn implements EventHandler {
    @Override
    public void run(Node currentNode, EventParam param) {
        VerificationResult result = (VerificationResult) param;
        long tid = result.tx.id;
        int newCnt = currentNode.verificationCnt.get(tid) + result.pass;
        currentNode.verificationCnt.put(tid, newCnt);
        int sonWaitCnt = currentNode.sonWaitCnt.get(tid);
        if (sonWaitCnt == 1) {
            int parent = currentNode.sendToTreeParent(new PreprepareReturn(), new VerificationResult(newCnt, result.tx),
                    result.tx.inputs.size() * 48 + result.tx.outputNum * 8 + 21 + 64 * newCnt);
            currentNode.sonWaitCnt.remove(tid);
            currentNode.verificationCnt.remove(tid);
            if (parent == 0) { // already root
                int threshold = ModelData.virtualShards.get(ModelData.virtualShardIndex.get(currentNode.getId()))
                        .size() * 2 / 3;
                if (newCnt > threshold) {
                    currentNode.sendToTreeSons(new Prepare(), new VerificationResult(newCnt, result.tx),
                            result.tx.inputs.size() * 48 + result.tx.outputNum * 8 + 20 + 64 * newCnt);
                }
                else {
                    // consensus not pass, unlock & terminate
                    for (TxInput input : result.tx.inputs) {
                        ModelData.unlockUTXO(input, result.tx.id);
                    }
                    currentNode.sendToTreeSons(new Abort(), new VerificationResult(newCnt, result.tx),
                            result.tx.inputs.size() * 48 + result.tx.outputNum * 8 + 20 + 64 * newCnt);
                }
            }
        }
        else
            currentNode.sonWaitCnt.put(tid, sonWaitCnt - 1);
    }
}

class Abort implements EventHandler {
    @Override
    public void run(Node currentNode, EventParam param) {
        VerificationResult result = (VerificationResult) param;
        currentNode.sendToTreeSons(new Abort(), param,result.tx.inputs.size() * 48 +
                result.tx.outputNum * 8 + 20 + result.pass * 64);
        // TODO: how long it takes to abort?
    }
}

class Prepare implements EventHandler {
    @Override
    public void run(Node currentNode, EventParam param) {
        VerificationResult result = (VerificationResult) param;
        currentNode.verificationCnt.put(result.tx.id, 1);
        int sons = currentNode.sendToTreeSons(new Prepare(), param,result.tx.inputs.size() * 48 +
                result.tx.outputNum * 8 + 20 + result.pass * 64);
        if (sons == 0) { // already leaf
            currentNode.sendToTreeParent(new PrepareReturn(), new VerificationResult(1, result.tx),
                    result.tx.inputs.size() * 48 + result.tx.outputNum * 8 + 21 + 64);
            currentNode.verificationCnt.remove(result.tx.id);
        }
        else
            currentNode.sonWaitCnt.put(result.tx.id, sons);
    }
}

class PrepareReturn implements EventHandler {
    @Override
    public void run(Node currentNode, EventParam param) {
        VerificationResult result = (VerificationResult) param;
        long tid = result.tx.id;
        int newCnt = currentNode.verificationCnt.get(tid) + result.pass;
        currentNode.verificationCnt.put(tid, newCnt);
        int sonWaitCnt = currentNode.sonWaitCnt.get(tid);
        if (sonWaitCnt == 1) {
            int parent = currentNode.sendToTreeParent(new PreprepareReturn(), param,result.tx.inputs.size() * 48
                    + result.tx.outputNum * 8 + 21 + result.pass * 64);
            currentNode.sonWaitCnt.remove(tid);
            currentNode.verificationCnt.remove(tid);
            if (parent == 0) { // already root

                // for statistics
                ModelData.ConsensusCnt++;
                for (TxInput input : result.tx.inputs) {
                    if (!ModelData.verifyUTXO(input, result.tx.id)) {
                        ModelData.FalseConsensusCnt++;
                        break;
                    }
                }

                // send to all related shards
                Set<Integer> involvedShards = new HashSet<>();
                for (TxInput input : result.tx.inputs) {
                    int responsibleShard = (int)input.tid % ModelData.shardNum;
                    involvedShards.add(responsibleShard);
                }
                int outputShard = (int)result.tx.id % ModelData.shardNum;
                involvedShards.add(outputShard);
                for (int shard : involvedShards) {
                    currentNode.sendToActualShard(shard, new Commit(), new VerificationResult(1, result.tx),
                            32 + result.tx.inputs.size() * 48 + result.tx.outputNum * 8 + 21 + 64 * newCnt);
                }
            }
        }
        else
            currentNode.sonWaitCnt.put(tid, sonWaitCnt - 1);
    }
}

class Commit implements EventHandler {
    @Override
    public void run(Node currentNode, EventParam param) {
        VerificationResult result = (VerificationResult) param;

        if (currentNode.receiveCommitSet.contains(result.tx.id))// replicate message
            return;
        currentNode.receiveCommitSet.add(result.tx.id);

        // TODO: add node commit time here

        if (ModelData.commitList.contains(result.tx.id))
            return;
        ModelData.commitList.add(result.tx.id);

        for (TxInput input : result.tx.inputs) {
            ModelData.useUTXO(input);
        }
        for (int i = 0; i < result.tx.outputNum; ++i) {
            ModelData.addUTXO(new TxInput(result.tx.id, i));
        }
        currentNode.sendOut(new ClientCommit(), param);
    }
}

class ClientCommit implements EventHandler {
    @Override
    public void run(Node currentNode, EventParam param) {
        TxStat.commit(((VerificationResult)param).tx);
    }
}