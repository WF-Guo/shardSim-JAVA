package edu.pku.infosec.customized;

import edu.pku.infosec.event.NodeAction;
import edu.pku.infosec.node.Node;
import edu.pku.infosec.transaction.TxInfo;
import edu.pku.infosec.transaction.TxInput;
import edu.pku.infosec.transaction.TxStat;

import java.awt.*;
import java.util.*;
import java.util.List;

public class TxProcessing implements NodeAction {
    private final TxInfo txInfo;

    public TxProcessing(TxInfo txInfo) {
        this.txInfo = txInfo;
    }

    @Override
    public void runOn(Node currentNode) {
        //the receiving node forwards the transaction
        Map<Integer, ArrayList<TxInput>> shardsMapping = new HashMap<>();
        for (TxInput input : txInfo.inputs) {
            int responsibleShard = (int) input.tid % ModelData.shardNum;
            if (!shardsMapping.containsKey(responsibleShard)) {
                shardsMapping.put(responsibleShard, new ArrayList<>());
            }
            ArrayList<TxInput> tmp = shardsMapping.get(responsibleShard);
            tmp.add(input);
            shardsMapping.put(responsibleShard, tmp);
        }
        int outputShard = (int) txInfo.id % ModelData.shardNum;
        if (!shardsMapping.containsKey(outputShard)) {
            shardsMapping.put(outputShard, new ArrayList<>()); // but no need to verify
        }

        ModelData.collectedVerification.put(txInfo.id, new ArrayList<>());

        Set<Integer> shardsSet = shardsMapping.keySet();
        Iterator<Integer> it = shardsSet.iterator();
        while (it.hasNext()) {
            int firstShard = it.next();
            List<TxInput> inputsToVerify = new ArrayList<>(shardsMapping.get(firstShard));
            if (it.hasNext()) {
                int secondShard = it.next();
                inputsToVerify.addAll(shardsMapping.get(secondShard));
                // the size is set to txInfo(x + 16) + inputNum(4) + inputNum * input(12)
                currentNode.sendToOverlapLeader(firstShard, secondShard,
                        new PreprepareVerify(new VerificationInfo(txInfo, inputsToVerify, firstShard, secondShard)),
                        txInfo.inputs.size() * 48 + txInfo.outputs.size() * 8 + 20 + inputsToVerify.size() * 12);
            } else {
                currentNode.sendToOverlapLeader(firstShard, firstShard,
                        new PreprepareVerify(new VerificationInfo(txInfo, inputsToVerify, firstShard, firstShard)),
                        txInfo.inputs.size() * 48 + txInfo.outputs.size() * 8 + 20 + inputsToVerify.size() * 12);
            }
        }
    }
}

class VerificationInfo {
    public final TxInfo tx;
    public final List<TxInput> inputs;
    public final int firstShard;
    public final int secondShard;

    public VerificationInfo(TxInfo txInfo, List<TxInput> inputs, int f, int s) {
        this.tx = txInfo;
        this.inputs = inputs;
        this.firstShard = f;
        this.secondShard = s;
    }
}

class PreprepareVerify implements NodeAction {
    private final VerificationInfo consensusParam;

    public PreprepareVerify(VerificationInfo consensusParam) {
        this.consensusParam = consensusParam;
    }

    @Override
    public void runOn(Node currentNode) {
        int verifyCnt = 0;
        if (currentNode.getId() >= ModelData.maliciousNum) {
            for (TxInput input : consensusParam.inputs) {
                verifyCnt++;
                if (!ModelData.verifyUTXO(input, consensusParam.tx.id)) {
                    currentNode.verificationCnt.put(consensusParam.tx.id, 0);
                    currentNode.stayBusy(ModelData.verificationTime * verifyCnt, new PreprepareFoward(consensusParam));
                    return;
                }
            }
        }
        currentNode.verificationCnt.put(consensusParam.tx.id, 1);
        currentNode.stayBusy(ModelData.verificationTime * verifyCnt, new PreprepareFoward(consensusParam));
    }
}

class VerificationResult {
    public final VerificationInfo vi;
    public final int pass;

    public VerificationResult(int pass, VerificationInfo vi) {
        this.pass = pass;
        this.vi = vi;
    }
}

class PreprepareFoward implements NodeAction {
    private final VerificationInfo consensusParam;

    public PreprepareFoward(VerificationInfo consensusParam) {
        this.consensusParam = consensusParam;
    }

    @Override
    public void runOn(Node currentNode) {
        int sons = currentNode.sendToTreeSons(new PreprepareVerify(consensusParam), consensusParam.tx.inputs.size() * 48 +
                consensusParam.tx.outputs.size() * 8 + 20 + consensusParam.inputs.size() * 12);
        if (sons == 0) { // already leaf
            currentNode.sendToTreeParent(
                    new PreprepareReturn(
                            new VerificationResult(currentNode.verificationCnt.get(consensusParam.tx.id), consensusParam)
                    ),
                    consensusParam.tx.inputs.size() * 48 + consensusParam.tx.outputs.size() * 8 + 21 +
                            consensusParam.inputs.size() * 12 + 64
            );
            currentNode.verificationCnt.remove(consensusParam.tx.id);
        } else
            currentNode.sonWaitCnt.put(consensusParam.tx.id, sons);
    }
}

class PreprepareReturn implements NodeAction {
    private final VerificationResult result;

    public PreprepareReturn(VerificationResult result) {
        this.result = result;
    }

    @Override
    public void runOn(Node currentNode) {
        long tid = result.vi.tx.id;
        int newCnt = currentNode.verificationCnt.get(tid) + result.pass;
        currentNode.verificationCnt.put(tid, newCnt);
        int sonWaitCnt = currentNode.sonWaitCnt.get(tid);
        if (sonWaitCnt == 1) {
            int parent = currentNode.sendToTreeParent(new PreprepareReturn(new VerificationResult(
                    newCnt, result.vi)), result.vi.tx.inputs.size() * 48 + result.vi.tx.outputs.size() * 8 + 21 +
                    result.vi.inputs.size() * 12 + 64 * newCnt);
            currentNode.sonWaitCnt.remove(tid);
            currentNode.verificationCnt.remove(tid);
            if (parent == 0) { // already root
                int threshold = ModelData.overlapShards.get(ModelData.originalShardIndex
                        .get(currentNode.getId())).size() * 2 / 3;
                if (newCnt > threshold) {
                    currentNode.verificationCnt.put(result.vi.tx.id, 1);
                    int sons = currentNode.sendToTreeSons(new Prepare(result), result.vi.tx.inputs.size() * 48 +
                            result.vi.tx.outputs.size() * 8 + 20 + result.vi.inputs.size() * 12 + 64 * newCnt);
                    currentNode.sonWaitCnt.put(result.vi.tx.id, sons);
                } else {
                    // send to all related shards a result 0
                    Set<Integer> involvedShards = new HashSet<>();
                    for (TxInput input : result.vi.tx.inputs) {
                        int responsibleShard = (int) input.tid % ModelData.shardNum;
                        involvedShards.add(responsibleShard);
                    }
                    int outputShard = (int) result.vi.tx.id % ModelData.shardNum;
                    involvedShards.add(outputShard);
                    for (int shard : involvedShards) {
                        currentNode.sendToOriginalShard(shard, new CheckCommit(new VerificationResult(0, result.vi)),
                                64 + result.vi.tx.inputs.size() * 48
                                        + result.vi.tx.outputs.size() * 8 + 21 + result.vi.inputs.size() * 12 + 64 * threshold);
                    }
                }
            }
        } else
            currentNode.sonWaitCnt.put(tid, sonWaitCnt - 1);
    }
}

class Prepare implements NodeAction {
    private final VerificationResult result;

    public Prepare(VerificationResult result) {
        this.result = result;
    }

    @Override
    public void runOn(Node currentNode) {
        currentNode.verificationCnt.put(result.vi.tx.id, 1);
        int sons = currentNode.sendToTreeSons(new Prepare(result), result.vi.tx.inputs.size() * 48 +
                result.vi.tx.outputs.size() * 8 + 20 + result.vi.inputs.size() * 12 + 64 * result.pass);
        if (sons == 0) { // already leaf
            currentNode.sendToTreeParent(new PrepareReturn(result), result.vi.tx.inputs.size() * 48 +
                    result.vi.tx.outputs.size() * 8 + 21 + result.vi.inputs.size() * 12 + 64);
            currentNode.verificationCnt.remove(result.vi.tx.id);
        } else
            currentNode.sonWaitCnt.put(result.vi.tx.id, sons);
    }
}

class PrepareReturn implements NodeAction {
    private final VerificationResult result;

    public PrepareReturn(VerificationResult result) {
        this.result = result;
    }

    @Override
    public void runOn(Node currentNode) {
        long tid = result.vi.tx.id;
        int newCnt = currentNode.verificationCnt.get(tid) + result.pass;
        currentNode.verificationCnt.put(tid, newCnt);
        int sonWaitCnt = currentNode.sonWaitCnt.get(tid);
        if (sonWaitCnt == 1) {
            int parent = currentNode.sendToTreeParent(new PrepareReturn(result), result.vi.tx.inputs.size() * 48
                    + result.vi.tx.outputs.size() * 8 + 21 + result.vi.inputs.size() * 12 + 64 * newCnt);
            currentNode.sonWaitCnt.remove(tid);
            currentNode.verificationCnt.remove(tid);
            if (parent == 0) { // already root

                // for statistics
                ModelData.ConsensusCnt++;
                for (TxInput input : result.vi.inputs) {
                    if (!ModelData.verifyUTXO(input, result.vi.tx.id)) {
                        ModelData.FalseConsensusCnt++;
                        break;
                    }
                }

                // send to all related shards
                Set<Integer> involvedShards = new HashSet<>();
                for (TxInput input : result.vi.tx.inputs) {
                    int responsibleShard = (int) input.tid % ModelData.shardNum;
                    involvedShards.add(responsibleShard);
                }
                int outputShard = (int) result.vi.tx.id % ModelData.shardNum;
                involvedShards.add(outputShard);
                for (int shard : involvedShards) {
                    currentNode.sendToOriginalShard(shard, new CheckCommit(new VerificationResult(1, result.vi)),
                            32 + result.vi.tx.inputs.size() * 48 + result.vi.tx.outputs.size() * 8
                                    + 21 + result.vi.inputs.size() * 12 + 64 * newCnt);
                }
            }
        } else
            currentNode.sonWaitCnt.put(tid, sonWaitCnt - 1);
    }
}

class CheckCommit implements NodeAction {
    private final VerificationResult result;

    public CheckCommit(VerificationResult result) {
        this.result = result;
    }

    @Override
    public void runOn(Node currentNode) {
        if (currentNode.receiveCommitSet.contains(result.vi.tx.id)) { // replicate message (at most once)
            currentNode.receiveCommitSet.remove(result.vi.tx.id);
            return;
        }
        currentNode.receiveCommitSet.add(result.vi.tx.id);

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
                int responsibleShard = (int) input.tid % ModelData.shardNum;
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
                currentNode.stayBusy(1, new CheckCommitPass(result));
            else if (alert)
                currentNode.stayBusy(ModelData.verificationTime * verifyCnt, new Alert(result));
            else
                currentNode.stayBusy(ModelData.verificationTime * verifyCnt, new CheckCommitPass(result));
        } else {
            // abort situation 1
            // TODO abort time
            ModelData.collectedVerification.remove(result.vi.tx.id);
            for (TxInput input : result.vi.tx.inputs) {
                ModelData.unlockUTXO(input, result.vi.tx.id);
            }
        }
    }
}

class CheckCommitPass implements NodeAction {
    private final VerificationResult result;

    public CheckCommitPass(VerificationResult result) {
        this.result = result;
    }

    @Override
    public void runOn(Node currentNode) {

        // collectedVerification is initialied in TxProcessing, removed in CheckCommit/CheckCommitPass
        if (ModelData.collectedVerification.containsKey(result.vi.tx.id)) {
            ModelData.collectedVerification.get(result.vi.tx.id).addAll(result.vi.inputs);
            if (ModelData.collectedVerification.get(result.vi.tx.id).size() == result.vi.tx.inputs.size()) {
                ModelData.collectedVerification.remove(result.vi.tx.id);
                for (TxInput input : result.vi.tx.inputs) {
                    ModelData.useUTXO(input);
                }
                for (int i = 0; i < result.vi.tx.outputs.size(); ++i) {
                    ModelData.addUTXO(new TxInput(result.vi.tx.id, i));
                }
                currentNode.sendOut(new Commit(result.vi.tx));
                // TODO commit time?
            }
        }
    }
}

class Commit implements NodeAction {
    private final TxInfo tx;

    public Commit(TxInfo tx) {
        this.tx = tx;
    }

    @Override
    public void runOn(Node currentNode) {
        // TODO: May be rolled back, but the interface is not implemented
        TxStat.confirm(tx);
    }
}

class RecheckInfo {
    public final TxInfo tx;
    public final TxInput input;
    public final int leader;

    public RecheckInfo(TxInfo txInfo, TxInput input, int leader) {
        this.tx = txInfo;
        this.input = input;
        this.leader = leader;
    }
}

class Alert implements NodeAction {
    private final VerificationResult result;

    public Alert(VerificationResult result) {
        this.result = result;
    }

    @Override
    public void runOn(Node currentNode) {
        currentNode.rollBackSignatureCnt.put(result.vi.tx.id, 0);
        currentNode.rollBackCnt.put(result.vi.tx.id, 0);
        for (TxInput input : result.vi.inputs) {
            int responsibleShard = (int) input.tid % ModelData.shardNum;
            if (!ModelData.verifyUTXO(input, result.vi.tx.id)) {
                currentNode.sendToHalfOriginalShard(responsibleShard, result.vi.tx.hashCode(),
                        new ReCheck(new RecheckInfo(result.vi.tx, input, currentNode.getId())),
                        result.vi.tx.inputs.size() * 48 + result.vi.tx.outputs.size() * 8 + 44);
                return;
            }
        }
    }
}

class ReCheck implements NodeAction {
    private final RecheckInfo consensusParam;

    public ReCheck(RecheckInfo consensusParam) {
        this.consensusParam = consensusParam;
    }

    @Override
    public void runOn(Node currentNode) {
        TxInput input = consensusParam.input;
        if (ModelData.verifyUTXO(input, consensusParam.tx.id) || currentNode.getId() < ModelData.maliciousNum)
            currentNode.stayBusy(ModelData.verificationTime, new VoteForPass(consensusParam));
        else
            currentNode.stayBusy(ModelData.verificationTime, new VoteForRollBack(consensusParam));
    }
}

class RecheckResult {
    public final RecheckInfo ri;
    public final boolean rollback;

    public RecheckResult(boolean rollback, RecheckInfo ri) {
        this.rollback = rollback;
        this.ri = ri;
    }
}

class VoteForPass implements NodeAction {
    private final RecheckInfo consensusParam;

    public VoteForPass(RecheckInfo consensusParam) {
        this.consensusParam = consensusParam;
    }

    @Override
    public void runOn(Node currentNode) {
        currentNode.sendMessage(consensusParam.leader, new CollectRecheck(new RecheckResult(false, consensusParam)),
                45 + consensusParam.tx.inputs.size() * 48 + consensusParam.tx.outputs.size() * 8);
    }
}

class VoteForRollBack implements NodeAction {
    private final RecheckInfo consensusParam;

    public VoteForRollBack(RecheckInfo consensusParam) {
        this.consensusParam = consensusParam;
    }

    @Override
    public void runOn(Node currentNode) {
        currentNode.sendMessage(consensusParam.leader, new CollectRecheck(new RecheckResult(true, consensusParam)),
                45 + consensusParam.tx.inputs.size() * 48 + consensusParam.tx.outputs.size() * 8);
    }
}

// only called by the leader
class CollectRecheck implements NodeAction {
    private final RecheckResult result;

    public CollectRecheck(RecheckResult result) {
        this.result = result;
    }

    @Override
    public void runOn(Node currentNode) {
        int newSignatureCnt = currentNode.rollBackSignatureCnt.get(result.ri.tx.id) + 1;
        currentNode.rollBackSignatureCnt.put(result.ri.tx.id, newSignatureCnt);
        int responsibleShard = (int) result.ri.input.tid % ModelData.shardNum;
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
                    int rollbackShard = (int) input.tid % ModelData.shardNum;
                    involvedShards.add(rollbackShard);
                }
                int outputShard = (int) result.ri.tx.id % ModelData.shardNum;
                involvedShards.add(outputShard);
                for (int shard : involvedShards) {
                    currentNode.sendToOriginalShard(shard, new RollBack(),
                            65 + result.ri.tx.inputs.size() * 48 + result.ri.tx.outputs.size() * 8);
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

class RollBack implements NodeAction {
    @Override
    public void runOn(Node currentNode) {
        // TODO: is 200ms a proper time?
        currentNode.stayBusy(200, new RollBackAction());
    }
}

class RollBackAction implements NodeAction {
    @Override
    public void runOn(Node currentNode) {
        // TODO: do something
    }
}
