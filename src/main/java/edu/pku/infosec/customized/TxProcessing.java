package edu.pku.infosec.customized;

import edu.pku.infosec.event.NodeAction;
import edu.pku.infosec.node.Node;
import edu.pku.infosec.transaction.TxInfo;
import edu.pku.infosec.transaction.TxInput;
import edu.pku.infosec.transaction.TxStat;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/*
        Define your transaction processing rule here
        You can use the following to describe your model behaviour:
        1. currentNode.getId() returns the id of local node, which may be useful
        2. call TxStat.commit(tid) after it is safe for user to confirm that transaction
        3. Implement NodeAction and corresponding EventParam to define a new event type
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

public class TxProcessing implements NodeAction {
    private final TxInfo txinfo;

    public TxProcessing(TxInfo txinfo) {
        this.txinfo = txinfo;
    }

    @Override
    public void runOn(Node currentNode) {
        //the receiving node forwards the transaction
        Set<Integer> shards = new HashSet<>();
        for (TxInput input : txinfo.inputs) {
            int responsibleShard = (int) input.tid % ModelData.shardNum;
            shards.add(responsibleShard);
        }
        int outputShard = (int) txinfo.id % ModelData.shardNum;
        shards.add(outputShard);

        Iterator<Integer> it = shards.iterator();
        int minContainShard = ModelData.shardParent.get(it.next());
        // minContainShard should be the smallest virtual shard containing all involved actual shard
        while (!ModelData.virtualShardContainList.get(minContainShard).containsAll(shards)) {
            minContainShard = ModelData.shardParent.get(minContainShard);
        }

        currentNode.sendToVirtualShardLeader(minContainShard, new PreprepareVerify(txinfo),
                txinfo.inputs.size() * ModelData.sizePerInput + txinfo.outputs.size() * ModelData.sizePerOutput +
                        ModelData.txOverhead);
    }
}

class PreprepareVerify implements NodeAction {
    private final TxInfo txinfo;

    public PreprepareVerify(TxInfo txinfo) {
        this.txinfo = txinfo;
    }

    @Override
    public void runOn(Node currentNode) {
        int verifyCnt = 0;
        if (currentNode.getId() >= ModelData.maliciousNum) {
            for (TxInput input : txinfo.inputs) {
                verifyCnt++;
                if (!ModelData.verifyUTXO(input, txinfo.id)) {
                    currentNode.obsentCnt.put(txinfo.id, 1);
                    currentNode.stayBusy((ModelData.verificationTime + 2 * ModelData.ECDSAPointMulTime +
                            ModelData.ECDSAPointAddTime) * verifyCnt, new PreprepareFoward(txinfo));
                    return;
                }
            }
        }
        currentNode.obsentCnt.put(txinfo.id, 0);
        currentNode.stayBusy((ModelData.verificationTime + 2 * ModelData.ECDSAPointMulTime +
                ModelData.ECDSAPointAddTime) * verifyCnt, new PreprepareFoward(txinfo));
    }
}

class PreprepareFoward implements NodeAction {
    private final TxInfo txinfo;

    public PreprepareFoward(TxInfo txinfo) {
        this.txinfo = txinfo;
    }

    @Override
    public void runOn(Node currentNode) {
        int sons = currentNode.sendToTreeSons(new PreprepareVerify(txinfo), txinfo.inputs.size() *
                ModelData.sizePerInput + txinfo.outputs.size() * ModelData.sizePerOutput + ModelData.txOverhead);
        if (sons == 0) { // already leaf
            currentNode.sendToTreeParent(
                    new PreprepareMerge(currentNode.obsentCnt.get(txinfo.id), txinfo), (1 +
                            currentNode.obsentCnt.get(txinfo.id)) * ModelData.ECDSAPointSize);
            currentNode.obsentCnt.remove(txinfo.id);
        } else
            currentNode.sonWaitCnt.put(txinfo.id, sons);
    }
}

class PreprepareMerge implements NodeAction {
    private final int cnt;
    private final TxInfo tx;

    public PreprepareMerge(int cnt, TxInfo tx) {
        this.cnt = cnt;
        this.tx = tx;
    }

    @Override
    public void runOn(Node currentNode) {
        long tid = tx.id;
        int newCnt = currentNode.obsentCnt.get(tid) + cnt;
        currentNode.obsentCnt.put(tid, newCnt);
        int sonWaitCnt = currentNode.sonWaitCnt.get(tid);
        if (sonWaitCnt == 1) {
            currentNode.stayBusy(2 * ModelData.ECDSAPointAddTime, new PreprepareReturn(newCnt, tx));
        } else
            currentNode.sonWaitCnt.put(tid, sonWaitCnt - 1);
    }
}

class PreprepareReturn implements NodeAction {
    private final int cnt;
    private final TxInfo tx;

    public PreprepareReturn(int cnt, TxInfo tx) {
        this.cnt = cnt;
        this.tx = tx;
    }

    @Override
    public void runOn(Node currentNode) {
        long tid = tx.id;
        int parent = currentNode.sendToTreeParent(new PreprepareMerge(cnt, tx),(1 + cnt) *
                ModelData.ECDSAPointSize);
        currentNode.sonWaitCnt.remove(tid);
        currentNode.obsentCnt.remove(tid);
        if (parent == 0) { // already root
            int threshold = ModelData.virtualShards.get(ModelData.virtualShardIndex.get(currentNode.getId()))
                    .size() / 3;
            if (cnt < threshold) {
                currentNode.stayBusy(ModelData.hashTimePerByte * (tx.inputs.size() *
                                ModelData.sizePerInput + tx.outputs.size() * ModelData.sizePerOutput +
                                ModelData.txOverhead + ModelData.hashSize), new StartPrepare(tx, cnt));
            } else {
                // consensus not pass, unlock & terminate
                for (TxInput input : tx.inputs) {
                    ModelData.unlockUTXO(input, tx.id);
                }
                currentNode.sendToTreeSons(new Abort(cnt, tx),1 + (1 + cnt) * ModelData.ECDSAPointSize);
            }
        }
    }
}

class StartPrepare implements NodeAction {
    private final TxInfo tx;
    private final int cnt;

    public StartPrepare(TxInfo tx, int cnt) {
        this.tx = tx;
        this.cnt = cnt;
    }

    @Override
    public void runOn(Node currentNode) {
        int sons = currentNode.sendToTreeSons(new Prepare(tx, cnt), ModelData.hashSize);
        currentNode.sonWaitCnt.put(tx.id, sons);
    }
}

class Abort implements NodeAction {
    private final int cnt;
    private final TxInfo tx;

    public Abort(int cnt, TxInfo tx) {
        this.cnt = cnt;
        this.tx = tx;
    }

    @Override
    public void runOn(Node currentNode) {
        currentNode.sendToTreeSons(new Abort(cnt, tx), 1 + (1 + cnt) * ModelData.ECDSAPointSize);
        // TODO: how long it takes to abort?
    }
}

class Prepare implements NodeAction {
    private final TxInfo tx;
    private final int cnt;

    public Prepare(TxInfo tx, int cnt) {
        this.tx = tx;
        this.cnt = cnt;
    }

    @Override
    public void runOn(Node currentNode) {
        int sons = currentNode.sendToTreeSons(new Prepare(tx, cnt),ModelData.hashSize);
        if (sons == 0) { // already leaf
            currentNode.sendToTreeParent(new PrepareReturn(tx, cnt),ModelData.ECDSANumberSize);
        } else
            currentNode.sonWaitCnt.put(tx.id, sons);
    }
}

class PrepareReturn implements NodeAction {
    private final TxInfo tx;
    private final int cnt;

    public PrepareReturn(TxInfo tx, int cnt) {
        this.tx = tx;
        this.cnt = cnt;
    }

    @Override
    public void runOn(Node currentNode) {
        long tid = tx.id;
        int sonWaitCnt = currentNode.sonWaitCnt.get(tid);
        if (sonWaitCnt == 1) {
            int parent = currentNode.sendToTreeParent(new PrepareReturn(tx, cnt), ModelData.ECDSANumberSize);
            currentNode.sonWaitCnt.remove(tid);
            if (parent == 0) { // already root

                // for statistics
                ModelData.ConsensusCnt++;
                for (TxInput input : tx.inputs) {
                    if (!ModelData.verifyUTXO(input, tx.id)) {
                        ModelData.FalseConsensusCnt++;
                        break;
                    }
                }

                // send to all related shards
                Set<Integer> involvedShards = new HashSet<>();
                for (TxInput input : tx.inputs) {
                    int responsibleShard = (int) input.tid % ModelData.shardNum;
                    involvedShards.add(responsibleShard);
                }
                int outputShard = (int) tx.id % ModelData.shardNum;
                involvedShards.add(outputShard);
                for (int shard : involvedShards) {
                    currentNode.sendToActualShard(shard, new Commit(tx),
                            1 + tx.inputs.size() * ModelData.sizePerInput + tx.outputs.size() *
                                    ModelData.sizePerOutput + ModelData.txOverhead + ModelData.ECDSANumberSize +
                                    cnt * ModelData.ECDSAPointSize);
                }
            }
        } else
            currentNode.sonWaitCnt.put(tid, sonWaitCnt - 1);
    }
}

class Commit implements NodeAction {
    private final TxInfo tx;

    public Commit(TxInfo tx) {
        this.tx = tx;
    }

    @Override
    public void runOn(Node currentNode) {

        if (currentNode.receiveCommitSet.contains(tx.id))// replicate message
            return;
        currentNode.receiveCommitSet.add(tx.id);

        for (TxInput input : tx.inputs) {
            ModelData.useUTXO(input);
        }
        for (TxInput output : tx.outputs) {
            ModelData.addUTXO(output);
        }
        currentNode.stayBusy(2 * ModelData.ECDSAPointMulTime + ModelData.ECDSAPointAddTime +
                ModelData.UTXORemoveTime * tx.inputs.size() + ModelData.UTXOAddTime * tx.outputs.size(), node->{});
        currentNode.sendOut(new ClientCommit(tx));
    }
}

class ClientCommit implements NodeAction {
    private final TxInfo tx;

    public ClientCommit(TxInfo tx) {
        this.tx = tx;
    }

    @Override
    public void runOn(Node currentNode) {
        TxStat.confirm(tx);
    }
}