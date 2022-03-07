package edu.pku.infosec.customized;

import edu.pku.infosec.event.EventHandler;
import edu.pku.infosec.event.EventParam;
import edu.pku.infosec.event.VoidEventParam;
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

// assume all nodes are honest. TODO: add node type where it can be malicious
public class TxProcessing implements EventHandler {
    @Override
    public void run(Node currentNode, EventParam param) {
        //the receiving node forwards the transaction
        TxInfo txinfo = (TxInfo) param;
        Map<Integer, ArrayList<TxInput>> shardsMapping = new HashMap<>();
        for (TxInput input : txinfo.inputs) {
            int responsibleShard = (int)input.tid % ModelData.shardNum;
            if (!shardsMapping.containsKey(responsibleShard)) {
                shardsMapping.put(responsibleShard, new ArrayList<>());
            }
            ArrayList<TxInput> tmp = shardsMapping.get(responsibleShard);
            tmp.add(input);
            shardsMapping.put(responsibleShard, tmp);
        }
        int outputShard = (int)txinfo.id % ModelData.shardNum;
        if (!shardsMapping.containsKey(outputShard)) {
            shardsMapping.put(outputShard, new ArrayList<>()); // but no need to verify
        }

        ModelData.collectedVerification.put(txinfo.id, new ArrayList<>());

        Set<Integer> shardsSet = shardsMapping.keySet();
        Iterator<Integer> it = shardsSet.iterator();
        while (it.hasNext()) {
            int firstShard = it.next();
            List<TxInput> inputsToVerify = new ArrayList<>(shardsMapping.get(firstShard));
            if (it.hasNext()) {
                int secondShard = it.next();
                inputsToVerify.addAll(shardsMapping.get(secondShard));
                // the size is set to txinfo(x + 16) + inputNum(4) + inputNum * input(12)
                currentNode.sendToOverlapLeader(firstShard, secondShard, new PreprepareVerify(),
                        new VerificationInfo(txinfo, inputsToVerify, firstShard, secondShard),
                        txinfo.inputs.size() * 48 + txinfo.outputNum * 8 + 20 + inputsToVerify.size() * 12);
            }
            else {
                currentNode.sendToOverlapLeader(firstShard, firstShard, new PreprepareVerify(),
                        new VerificationInfo(txinfo, inputsToVerify, firstShard, firstShard),
                        txinfo.inputs.size() * 48 + txinfo.outputNum * 8 + 20 + inputsToVerify.size() * 12);
            }
        }
    }
}

class VerificationInfo extends EventParam {
    public final TxInfo tx;
    public final List<TxInput> inputs;
    public final int firstShard;
    public final int secondShard;

    public VerificationInfo(TxInfo txinfo, List<TxInput> inputs, int f, int s) {
        this.tx = txinfo;
        this.inputs = inputs;
        this.firstShard = f;
        this.secondShard = s;
    }
}

class PreprepareVerify implements EventHandler {
    @Override
    public void run(Node currentNode, EventParam param) {
        VerificationInfo consensusParam = (VerificationInfo) param;
        int verifyCnt = 0;
        if (currentNode.getId() >= ModelData.maliciousNum) {
            for (TxInput input : consensusParam.inputs) {
                verifyCnt++;
                if (!ModelData.verifyUTXO(input, consensusParam.tx.id)) {
                    currentNode.verificationCnt.put(consensusParam.tx.id, 0);
                    currentNode.stayBusy(ModelData.verificationTime * verifyCnt, new PreprepareFoward(),param);
                    return;
                }
            }
        }
        currentNode.verificationCnt.put(consensusParam.tx.id, 1);
        currentNode.stayBusy(ModelData.verificationTime * verifyCnt, new PreprepareFoward(), param);
    }
}

class VerificationResult extends EventParam {
    public final VerificationInfo vi;
    public final int pass;

    public VerificationResult(int pass, VerificationInfo vi) {
        this.pass = pass;
        this.vi = vi;
    }
}

class PreprepareFoward implements EventHandler {
    @Override
    public void run(Node currentNode, EventParam param) {
        VerificationInfo consensusParam = (VerificationInfo) param;
        int sons = currentNode.sendToTreeSons(new PreprepareVerify(), param,consensusParam.tx.inputs.size() * 48 +
                consensusParam.tx.outputNum * 8 + 20 + consensusParam.inputs.size() * 12);
        if (sons == 0) { // already leaf
            currentNode.sendToTreeParent(new PreprepareReturn(), new VerificationResult(
                    currentNode.verificationCnt.get(consensusParam.tx.id), consensusParam),
                    consensusParam.tx.inputs.size() * 48 + consensusParam.tx.outputNum * 8 + 21 +
                    consensusParam.inputs.size() * 12 + 64);
        }
        else
            currentNode.sonWaitCnt.put(consensusParam.tx.id, sons);
    }
}

class PreprepareReturn implements EventHandler {
    @Override
    public void run(Node currentNode, EventParam param) {
        VerificationResult result = (VerificationResult) param;
        long tid = result.vi.tx.id;
        int newCnt = currentNode.verificationCnt.get(tid) + result.pass;
        currentNode.verificationCnt.put(tid, newCnt);
        int sonWaitCnt = currentNode.sonWaitCnt.get(tid);
        if (sonWaitCnt == 1) {
            int parent = currentNode.sendToTreeParent(new PreprepareReturn(), new VerificationResult(
                            newCnt, result.vi), result.vi.tx.inputs.size() * 48 + result.vi.tx.outputNum * 8 + 21 +
                            result.vi.inputs.size() * 12 + 64);
            currentNode.sonWaitCnt.remove(tid);
            currentNode.verificationCnt.remove(tid);
            if (parent == 0) { // already root
                int threshold = ModelData.overlapShards.get(ModelData.originalShardIndex
                        .get(currentNode.getId())).size() * 2 / 3;
                if (newCnt > threshold) {
                    currentNode.sendToTreeSons(new Prepare(), result.vi,result.vi.tx.inputs.size() * 48 +
                            result.vi.tx.outputNum * 8 + 20 + result.vi.inputs.size() * 12);
                }
                else {
                    // send to all related shards a result 0
                    Set<Integer> involvedShards = new HashSet<>();
                    for (TxInput input : result.vi.tx.inputs) {
                        int responsibleShard = (int)input.tid % ModelData.shardNum;
                        involvedShards.add(responsibleShard);
                    }
                    int outputShard = (int)result.vi.tx.id % ModelData.shardNum;
                    involvedShards.add(outputShard);
                    for (int shard : involvedShards) {
                        currentNode.sendToOriginalShard(shard, new CheckCommit(),
                                new VerificationResult(0, result.vi),64 + result.vi.tx.inputs.size() * 48
                                        + result.vi.tx.outputNum * 8 + 21 + result.vi.inputs.size() * 12);
                    }
                }
            }
        }
        else
            currentNode.sonWaitCnt.put(tid, sonWaitCnt - 1);
    }
}

class Prepare implements EventHandler {
    @Override
    public void run(Node currentNode, EventParam param) {
        VerificationInfo consensusParam = (VerificationInfo) param;
        int sons = currentNode.sendToTreeSons(new Prepare(), param,consensusParam.tx.inputs.size() * 48 +
                consensusParam.tx.outputNum * 8 + 20 + consensusParam.inputs.size() * 12);
        if (sons == 0) { // already leaf
            currentNode.sendToTreeParent(new PrepareReturn(), param, consensusParam.tx.inputs.size() * 48 +
                    consensusParam.tx.outputNum * 8 + 21 + consensusParam.inputs.size() * 12 + 64);
        }
        else
            currentNode.sonWaitCnt.put(consensusParam.tx.id, sons);
    }
}

class PrepareReturn implements EventHandler {
    @Override
    public void run(Node currentNode, EventParam param) {
        VerificationInfo result = (VerificationInfo) param;
        long tid = result.tx.id;
        int sonWaitCnt = currentNode.sonWaitCnt.get(tid);
        if (sonWaitCnt == 1) {
            int parent = currentNode.sendToTreeParent(new PreprepareReturn(), param,result.tx.inputs.size() * 48
                    + result.tx.outputNum * 8 + 21 + result.inputs.size() * 12 + 64);
            currentNode.sonWaitCnt.remove(tid);
            if (parent == 0) { // already root

                // for statistics
                ModelData.ConsensusCnt++;
                for (TxInput input : result.inputs) {
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
                    currentNode.sendToOriginalShard(shard, new CheckCommit(), new VerificationResult(1, result),
                            32 + result.tx.inputs.size() * 48 + result.tx.outputNum * 8
                                    + 21 + result.inputs.size() * 12);
                }
            }
        }
        else
            currentNode.sonWaitCnt.put(tid, sonWaitCnt - 1);
    }
}

class CheckCommit implements EventHandler {
    @Override
    public void run(Node currentNode, EventParam param) {
        VerificationResult result = (VerificationResult) param;

        if (!ModelData.collectedVerification.containsKey(result.vi.tx.id)) // already commited or aborted
            return;

        // TODO: how can we count a transaction as commited by the system?
        if (result.pass == 1) {
            // first check again

            shardPair nodeOriginalShards = ModelData.originalShardIndex.get(currentNode.getId());
            int verifyCnt = 0;
            int firstShard = -1, secondShard = -1;
            boolean alert = false;
            for (TxInput input : result.vi.inputs) {
                int responsibleShard = (int)input.tid % ModelData.shardNum;
                if (firstShard == -1)
                    firstShard = responsibleShard;
                else if (firstShard != responsibleShard)
                    secondShard = responsibleShard;
                verifyCnt++;
                if (!ModelData.verifyUTXO(input, result.vi.tx.id)) {
                    alert = true;
                    break;
                }
            }
            if (secondShard == -1)
                secondShard = firstShard;
            if (!Objects.equals(nodeOriginalShards, new shardPair(firstShard, secondShard)))
                currentNode.stayBusy(1, new CheckCommitPass(), param);
            else if (alert)
                currentNode.stayBusy(ModelData.verificationTime * verifyCnt, new Alert(), param);
            else
                currentNode.stayBusy(ModelData.verificationTime * verifyCnt, new CheckCommitPass(), param);
        }
        else {
            // abort situation 1
            ModelData.collectedVerification.remove(result.vi.tx.id);
            for (TxInput input : result.vi.tx.inputs) {
                ModelData.unlockUTXO(input, result.vi.tx.id);
            }
        }
    }
}

class CheckCommitPass implements EventHandler {
    @Override
    public void run(Node currentNode, EventParam param) {
        VerificationResult result = (VerificationResult) param;

        // collectedVerification is initialied in TxProcessing, removed in CheckCommit/CheckCommitPass
        if (ModelData.collectedVerification.containsKey(result.vi.tx.id)) {
            ModelData.collectedVerification.get(result.vi.tx.id).addAll(result.vi.inputs);
            if (ModelData.collectedVerification.get(result.vi.tx.id).size() == result.vi.tx.inputs.size()) {
                ModelData.collectedVerification.remove(result.vi.tx.id);
                for (TxInput input : result.vi.tx.inputs) {
                    ModelData.useUTXO(input);
                }
                for (int i = 0; i < result.vi.tx.outputNum; ++i) {
                    ModelData.addUTXO(new TxInput(result.vi.tx.id, i));
                }
                currentNode.sendOut(new Commit(), param);
            }
        }
    }
}

class Commit implements EventHandler {
    @Override
    public void run(Node currentNode, EventParam param) {
        // TODO: May be rolled back, but the interface is not implemented
        TxStat.commit(((VerificationResult)param).vi.tx);
    }
}

class RecheckInfo extends EventParam {
    public final TxInfo tx;
    public final TxInput input;
    public final int leader;

    public RecheckInfo(TxInfo txinfo, TxInput input, int leader) {
        this.tx = txinfo;
        this.input = input;
        this.leader = leader;
    }
}

class Alert implements EventHandler {
    @Override
    public void run(Node currentNode, EventParam param) {
        VerificationResult result = (VerificationResult) param;
        currentNode.rollBackSignatureCnt.put(result.vi.tx.id, 0);
        currentNode.rollBackCnt.put(result.vi.tx.id, 0);
        for (TxInput input : result.vi.inputs) {
            int responsibleShard = (int)input.tid % ModelData.shardNum;
            if (!ModelData.verifyUTXO(input, result.vi.tx.id)) {
                currentNode.sendToHalfOriginalShard(responsibleShard, result.vi.tx.hashCode(), new ReCheck(),
                        new RecheckInfo(result.vi.tx, input, currentNode.getId()),
                        result.vi.tx.inputs.size() * 48 + result.vi.tx.outputNum * 8 + 44);
                return;
            }
        }
    }
}

class ReCheck implements EventHandler {
    @Override
    public void run(Node currentNode, EventParam param) {
        RecheckInfo consensusParam = (RecheckInfo) param;
        TxInput input = consensusParam.input;
        if (ModelData.verifyUTXO(input, consensusParam.tx.id) || currentNode.getId() < ModelData.maliciousNum)
            currentNode.stayBusy(ModelData.verificationTime, new VoteForPass(), param);
        else
            currentNode.stayBusy(ModelData.verificationTime, new VoteForRollBack(), param);
    }
}

class RecheckResult extends EventParam {
    public final RecheckInfo ri;
    public final boolean rollback;

    public RecheckResult(boolean rollback, RecheckInfo ri) {
        this.rollback = rollback;
        this.ri = ri;
    }
}

class VoteForPass implements EventHandler {
    @Override
    public void run(Node currentNode, EventParam param) {
        RecheckInfo consensusParam = (RecheckInfo) param;
        currentNode.sendMessage(consensusParam.leader, new CollectRecheck(),
                new RecheckResult(false, consensusParam),
                45 + consensusParam.tx.inputs.size() * 48 + consensusParam.tx.outputNum * 8);
    }
}

class VoteForRollBack implements EventHandler {
    @Override
    public void run(Node currentNode, EventParam param) {
        RecheckInfo consensusParam = (RecheckInfo) param;
        currentNode.sendMessage(consensusParam.leader, new CollectRecheck(),
                new RecheckResult(true, consensusParam),
                45 + consensusParam.tx.inputs.size() * 48 + consensusParam.tx.outputNum * 8);
    }
}

// only called by the leader
class CollectRecheck implements EventHandler {
    @Override
    public void run(Node currentNode, EventParam param) {
        RecheckResult result = (RecheckResult) param;
        int newSignatureCnt = currentNode.rollBackSignatureCnt.get(result.ri.tx.id) + 1;
        currentNode.rollBackSignatureCnt.put(result.ri.tx.id, newSignatureCnt);
        int responsibleShard = (int)result.ri.input.tid % ModelData.shardNum;
        int participateNumber = 0;
        for (int i = 0; i < ModelData.shardNum; ++i) {
            List<Integer> nodes = ModelData.overlapShards.get(new shardPair(responsibleShard, i));
            for (int node : nodes) {
                if (node != currentNode.getId() && (Objects.hash(node, result.ri.tx.hashCode())) % 2 == 0)
                    participateNumber++;
            }
        }
        if (result.rollback) {
            int newRollBackCnt = currentNode.rollBackCnt.get(result.ri.tx.id) + 1;
            int threshold = participateNumber * 2 / 3;
            if (newRollBackCnt > threshold) {
                currentNode.rollBackCnt.remove(result.ri.tx.id);
                currentNode.rollBackSignatureCnt.remove(result.ri.tx.id);
                // TODO: need an interface to rollback
                // abort situation 2
                ModelData.collectedVerification.remove(result.ri.tx.id);
                for (TxInput input : result.ri.tx.inputs) {
                    ModelData.unlockUTXO(input, result.ri.tx.id);
                }

                // send to all related shards
                Set<Integer> involvedShards = new HashSet<>();
                for (TxInput input : result.ri.tx.inputs) {
                    int rollbackShard = (int)input.tid % ModelData.shardNum;
                    involvedShards.add(rollbackShard);
                }
                int outputShard = (int)result.ri.tx.id % ModelData.shardNum;
                involvedShards.add(outputShard);
                for (int shard : involvedShards) {
                    currentNode.sendToOriginalShard(shard, new RollBack(), param,
                            65 + result.ri.tx.inputs.size() * 48 + result.ri.tx.outputNum * 8);
                }
                return;
            }
            currentNode.rollBackCnt.put(result.ri.tx.id, newRollBackCnt);
        }
        // consensus failed when every node has voted yet not enough passes are collected
        if (newSignatureCnt == participateNumber) {
            currentNode.rollBackCnt.remove(result.ri.tx.id);
            currentNode.rollBackSignatureCnt.remove(result.ri.tx.id);
            // TODO: if recheck decides not to roll back, nothing need to be done now,
            // TODO: but we may add punishment for fake alert
        }
    }
}

class RollBack implements EventHandler {
    @Override
    public void run(Node currentNode, EventParam param) {
        // TODO: is 200ms a proper time?
        currentNode.stayBusy(200, new RollBackAction(), new VoidEventParam());
    }
}

class RollBackAction implements EventHandler {
    @Override
    public void run(Node currentNode, EventParam param) {
        // TODO: do something
    }
}