package edu.pku.infosec.customized;

import edu.pku.infosec.event.EventDriver;
import edu.pku.infosec.event.NodeAction;
import edu.pku.infosec.node.Node;
import edu.pku.infosec.transaction.TxInfo;
import edu.pku.infosec.transaction.TxInput;
import edu.pku.infosec.transaction.TxStat;
import edu.pku.infosec.util.GroupedSet;
import edu.pku.infosec.util.RandomQueue;

import java.util.*;
import java.util.List;

public class TxProcessing implements NodeAction {
    private final TxInfo txInfo;

    public TxProcessing(TxInfo txInfo) {
        this.txInfo = txInfo;
    }

    @Override
    public void runOn(Node currentNode) {
        /*
        Date time = new Date();
        ModelData.SYSTIME = time.getTime();
         */

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

        ModelData.collectedVerification.put(txInfo.id, new HashSet<>());

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
                        txInfo.inputs.size() * ModelData.sizePerInput +
                                txInfo.outputs.size() * ModelData.sizePerOutput + ModelData.txOverhead);
            } else {
                currentNode.sendToOverlapLeader(firstShard, firstShard,
                        new PreprepareVerify(new VerificationInfo(txInfo, inputsToVerify, firstShard, firstShard)),
                        txInfo.inputs.size() * ModelData.sizePerInput +
                                txInfo.outputs.size() * ModelData.sizePerOutput + ModelData.txOverhead);
            }
        }

        /*
        if (currentNode.getId() == 233) {
            System.out.println("in TxProcessing Time Used: " + (time.getTime() - ModelData.SYSTIME));
        }
         */
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
                    currentNode.obsentCnt.put(consensusParam.tx.id, 1);
                    currentNode.stayBusy((ModelData.verificationTime + 2 * ModelData.ECDSAPointMulTime +
                            ModelData.ECDSAPointAddTime) * verifyCnt, new PreprepareFoward(consensusParam));
                    return;
                }
            }
        }
        currentNode.obsentCnt.put(consensusParam.tx.id, 0);
        currentNode.stayBusy((ModelData.verificationTime + 2 * ModelData.ECDSAPointMulTime +
                ModelData.ECDSAPointAddTime) * verifyCnt, new PreprepareFoward(consensusParam));
    }
}

class VerificationResult {
    public final VerificationInfo vi;
    public final int cnt;

    public VerificationResult(int cnt, VerificationInfo vi) {
        this.cnt = cnt;
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

        int sons = currentNode.sendToTreeSons(new PreprepareVerify(consensusParam),
                consensusParam.tx.inputs.size() * ModelData.sizePerInput +
                        consensusParam.tx.outputs.size() * ModelData.sizePerOutput + ModelData.txOverhead);
        if (sons == 0) { // already leaf
            currentNode.sendToTreeParent(new PreprepareMerge(new VerificationResult(currentNode.obsentCnt
                    .get(consensusParam.tx.id), consensusParam)), (1 +
                    currentNode.obsentCnt.get(consensusParam.tx.id)) * ModelData.ECDSAPointSize);
            currentNode.obsentCnt.remove(consensusParam.tx.id);
        } else
            currentNode.sonWaitCnt.put(consensusParam.tx.id, sons);

    }
}

class PreprepareMerge implements NodeAction {
    private final VerificationResult result;

    public PreprepareMerge(VerificationResult result) {
        this.result = result;
    }

    @Override
    public void runOn(Node currentNode) {

        long tid = result.vi.tx.id;
        int newCnt = currentNode.obsentCnt.get(tid) + result.cnt;
        currentNode.obsentCnt.put(tid, newCnt);
        int sonWaitCnt = currentNode.sonWaitCnt.get(tid);
        if (sonWaitCnt == 1) {
            currentNode.stayBusy(2 * ModelData.ECDSAPointAddTime, new PreprepareReturn(result));
        } else
            currentNode.sonWaitCnt.put(tid, sonWaitCnt - 1);

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
        int newCnt = currentNode.obsentCnt.get(tid);

        int parent = currentNode.sendToTreeParent(new PreprepareMerge(new VerificationResult(
                newCnt, result.vi)), (1 + newCnt) * ModelData.ECDSAPointSize);
        currentNode.sonWaitCnt.remove(tid);
        currentNode.obsentCnt.remove(tid);
        if (parent == 0) { // already root
            int threshold = ModelData.overlapShards.get(ModelData.originalShardIndex
                        .get(currentNode.getId())).size() / 3;
            if (newCnt < threshold) {
                currentNode.stayBusy(ModelData.hashTimePerByte * (result.vi.tx.inputs.size() *
                        ModelData.sizePerInput + result.vi.tx.outputs.size() * ModelData.sizePerOutput +
                        ModelData.txOverhead + ModelData.hashSize),
                        new StartPrepare(new VerificationResult(newCnt, result.vi)));
            } else {
                // send to all related shards a result 0
                Set<Integer> involvedShards = new HashSet<>();
                Set<Integer> involvedNodes = new HashSet<>();
                for (TxInput input : result.vi.tx.inputs) {
                    int responsibleShard = (int) input.tid % ModelData.shardNum;
                    involvedShards.add(responsibleShard);
                }
                int outputShard = (int) result.vi.tx.id % ModelData.shardNum;
                involvedShards.add(outputShard);
                for (int shard : involvedShards) {
                    for (int i = 0; i < ModelData.shardNum; ++i) {
                        involvedNodes.addAll(ModelData.overlapShards.get(new shardPair(shard, i)));
                    }
                }
                for (int node : involvedNodes) {
                    currentNode.sendMessage(node, new CheckCommit(new VerificationResult(0, result.vi)),
                            ModelData.hashSize + 1 + (1 + newCnt) * ModelData.ECDSAPointSize);
                }
            }
        }

    }
}

class StartPrepare implements NodeAction {
    private final VerificationResult result;

    public StartPrepare(VerificationResult result) {
        this.result = result;
    }

    @Override
    public void runOn(Node currentNode) {

        /* memory check
        if (result.vi.tx.id % 1000 == 0) {
            System.out.println("collectedVerification " + ModelData.collectedVerification.size());
            System.out.println("CommittedTransactions " + ModelData.CommittedTransactions.size());
            System.out.println("ModelUTXO " + ModelData.UTXO.size());
            System.out.println("lockedUTXO " + ModelData.lockedUTXO.size());
            System.out.println("receiveCommitSet " + currentNode.receiveCommitSet.size() * 1024);
            System.out.println("sonWaitCnt " + currentNode.sonWaitCnt.size() * 1024);
            System.out.println("rollBackCnt " + currentNode.rollBackCnt.size() * 1024);
            System.out.println("rollBackSignatureCnt " + currentNode.rollBackSignatureCnt.size() * 1024);
            System.out.println("obsentCnt " + currentNode.obsentCnt.size() * 1024);
        }
         */

        int sons = currentNode.sendToTreeSons(new Prepare(result), ModelData.hashSize);
        currentNode.sonWaitCnt.put(result.vi.tx.id, sons);

    }
}

class Prepare implements NodeAction {
    private final VerificationResult result;

    public Prepare(VerificationResult result) {
        this.result = result;
    }

    @Override
    public void runOn(Node currentNode) {

        int sons = currentNode.sendToTreeSons(new Prepare(result), ModelData.hashSize);
        if (sons == 0) { // already leaf
            currentNode.sendToTreeParent(new PrepareReturn(result), ModelData.ECDSANumberSize);
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
        int sonWaitCnt = currentNode.sonWaitCnt.get(tid);
        if (sonWaitCnt == 1) {
            int parent = currentNode.sendToTreeParent(new PrepareReturn(result), ModelData.ECDSANumberSize);
            currentNode.sonWaitCnt.remove(tid);
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
                Set<Integer> involvedNodes = new HashSet<>();
                for (TxInput input : result.vi.tx.inputs) {
                    int responsibleShard = (int) input.tid % ModelData.shardNum;
                    involvedShards.add(responsibleShard);
                }
                int outputShard = (int) result.vi.tx.id % ModelData.shardNum;
                involvedShards.add(outputShard);
                for (int shard : involvedShards) {
                    for (int i = 0; i < ModelData.shardNum; ++i) {
                        involvedNodes.addAll(ModelData.overlapShards.get(new shardPair(shard, i)));
                    }
                }
                for (int node : involvedNodes) {
                    currentNode.sendMessage(node, new CheckCommit(new VerificationResult(1, result.vi)),
                            1 + result.vi.tx.inputs.size() * ModelData.sizePerInput +
                                    result.vi.tx.outputs.size() * ModelData.sizePerOutput + ModelData.txOverhead +
                                    ModelData.ECDSANumberSize + result.cnt * ModelData.ECDSAPointSize);
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

        // TODO: how can we count a transaction as commited by the system?
        if (result.cnt == 1) {
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
                if ((!ModelData.verifyUTXO(input, result.vi.tx.id)) && currentNode.getId() >= ModelData.maliciousNum) {
                    if (ModelData.CommittedTransactions.contains(result.vi.tx.id))
                        return;
                    alert = true;
                    break;
                }
            }
            if (secondShard == -1)
                secondShard = firstShard;
            if (!Objects.equals(nodeOriginalShards, new shardPair(firstShard, secondShard)))
                currentNode.stayBusy(2 * ModelData.ECDSAPointMulTime + ModelData.ECDSAPointAddTime,
                        new CheckCommitPass(result));
            else if (alert)
                currentNode.stayBusy(2 * ModelData.ECDSAPointMulTime + ModelData.ECDSAPointAddTime +
                        (ModelData.verificationTime + 2 * ModelData.ECDSAPointMulTime + ModelData.ECDSAPointAddTime) *
                                verifyCnt + ModelData.hashTimePerByte * (result.vi.tx.inputs.size() *
                        ModelData.sizePerInput + result.vi.tx.outputs.size() * ModelData.sizePerOutput +
                        ModelData.txOverhead + 1), new Alert(result));
            else
                currentNode.stayBusy(2 * ModelData.ECDSAPointMulTime + ModelData.ECDSAPointAddTime +
                        (ModelData.verificationTime + 2 * ModelData.ECDSAPointMulTime + ModelData.ECDSAPointAddTime) *
                                verifyCnt, new CheckCommitPass(result));
        } else {
            // abort situation 1
            ModelData.collectedVerification.remove(result.vi.tx.id);
            for (TxInput input : result.vi.tx.inputs) {
                ModelData.unlockUTXO(input, result.vi.tx.id);
            }
            // abort time
            currentNode.stayBusy(ModelData.UTXORemoveTime * result.vi.tx.inputs.size(), node->{});
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

                boolean canCommit = true;
                for (TxInput input : result.vi.tx.inputs) {
                    if (!ModelData.verifyUTXO(input, result.vi.tx.id)) {
                        canCommit = false;
                        break;
                    }
                }
                if (canCommit) {
                    ModelData.CommittedTransactions.add(result.vi.tx.id);
                    for (TxInput input : result.vi.tx.inputs) {
                        ModelData.useUTXO(input);
                    }
                    for (int i = 0; i < result.vi.tx.outputs.size(); ++i) {
                        ModelData.addUTXO(new TxInput(result.vi.tx.id, i));
                    }
                    currentNode.sendOut(new Commit(result.vi.tx));
                }

                // commit time?
                currentNode.stayBusy(ModelData.UTXORemoveTime * result.vi.tx.inputs.size()
                        + ModelData.UTXOAddTime * result.vi.tx.outputs.size(), node->{});
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
                        result.vi.tx.inputs.size() * ModelData.sizePerInput + result.vi.tx.outputs.size() *
                                ModelData.sizePerOutput + ModelData.txOverhead + 1 + ModelData.ECDSAPointSize +
                        ModelData.ECDSANumberSize);
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
            currentNode.stayBusy((ModelData.verificationTime + 2 * ModelData.ECDSAPointMulTime +
                    ModelData.ECDSAPointAddTime) + ModelData.hashTimePerByte * (1 + ModelData.hashSize),
                    new VoteForPass(consensusParam));
        else
            currentNode.stayBusy((ModelData.verificationTime + 2 * ModelData.ECDSAPointMulTime +
                    ModelData.ECDSAPointAddTime) + ModelData.hashTimePerByte * (1 + ModelData.hashSize),
                    new VoteForRollBack(consensusParam));
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
        currentNode.sendMessage(consensusParam.leader, new VerifyRecheck(new RecheckResult(false, consensusParam)),
                ModelData.ECDSAPointSize * 2 + ModelData.ECDSANumberSize + 1 + ModelData.hashSize);
    }
}

class VoteForRollBack implements NodeAction {
    private final RecheckInfo consensusParam;

    public VoteForRollBack(RecheckInfo consensusParam) {
        this.consensusParam = consensusParam;
    }

    @Override
    public void runOn(Node currentNode) {
        currentNode.sendMessage(consensusParam.leader, new VerifyRecheck(new RecheckResult(true, consensusParam)),
                ModelData.ECDSAPointSize * 2 + ModelData.ECDSANumberSize + 1 + ModelData.hashSize);
    }
}

class VerifyRecheck implements NodeAction {
    private final RecheckResult result;

    public VerifyRecheck(RecheckResult result) {
        this.result = result;
    }

    @Override
    public void runOn(Node currentNode) {

        currentNode.stayBusy(2 * ModelData.ECDSAPointMulTime + ModelData.ECDSAPointAddTime,
                new CollectRecheck(result));

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

        if (!currentNode.rollBackSignatureCnt.containsKey(result.ri.tx.id))
            return;
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
                Set<Integer> involvedNodes = new HashSet<>();
                for (TxInput input : result.ri.tx.inputs) {
                    int rollbackShard = (int) input.tid % ModelData.shardNum;
                    involvedShards.add(rollbackShard);
                }
                int outputShard = (int) result.ri.tx.id % ModelData.shardNum;
                involvedShards.add(outputShard);
                for (int shard : involvedShards) {
                    for (int i = 0; i < ModelData.shardNum; ++i) {
                        involvedNodes.addAll(ModelData.overlapShards.get(new shardPair(shard, i)));
                    }
                }
                for (int node : involvedNodes) {
                    currentNode.sendMessage(node, new RollBack(result.ri.tx), ModelData.hashSize +
                            newRollBackCnt * (ModelData.ECDSAPointSize * 2 + ModelData.ECDSANumberSize));
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
    private final TxInfo tx;

    public RollBack(TxInfo tx) {
        this.tx = tx;
    }
    @Override
    public void runOn(Node currentNode) {
        currentNode.stayBusy(tx.inputs.size() * ModelData.UTXOAddTime + tx.outputs.size() *
                ModelData.UTXORemoveTime, node->{});
    }
}
