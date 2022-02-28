package edu.pku.infosec.customized;

import com.alibaba.fastjson.JSONObject;
import edu.pku.infosec.event.NodeAction;
import edu.pku.infosec.node.Node;
import edu.pku.infosec.transaction.TxInfo;
import edu.pku.infosec.transaction.TxStat;
import edu.pku.infosec.util.RandomChoose;

import java.util.Random;

public class TxGeneration implements NodeAction {
    private final Random random = new Random();
    private final JSONObject inputNumDistribution;
    private final JSONObject outputNumDistribution;
    double DSAttackRate;

    public TxGeneration(JSONObject txConfig) {
        DSAttackRate = txConfig.getDouble("DSAttackRate");
        inputNumDistribution = txConfig.getJSONObject("inputNum");
        outputNumDistribution = txConfig.getJSONObject("outputNum");
    }

    @Override
    public void runOn(Node currentNode) {
        TxInfo tx = new TxInfo();
        int inputNum = RandomChoose.chooseByDistribution(inputNumDistribution);
        tx.outputNum = RandomChoose.chooseByDistribution(outputNumDistribution);
        for (int i = 0; i < inputNum && TxStat.utxoSize() > 0; i++)
            tx.inputs.add(TxStat.getRandomUTXO());
        TxStat.submit(tx);
        currentNode.sendIn(random.nextInt(ModelData.nodeNum), new TxProcessing());
        if (random.nextDouble() < DSAttackRate && tx.inputs.size() > 0) {
            TxInfo attack = new TxInfo();
            attack.outputNum = tx.outputNum;
            attack.inputs.add(tx.inputs.get(0));
            for (int i = 1; i < inputNum && TxStat.utxoSize() > 0; i++)
                attack.inputs.add(TxStat.getRandomUTXO());
            TxStat.submit(attack);
            TxStat.markConflict(tx, attack);
            currentNode.sendIn(random.nextInt(ModelData.nodeNum), new TxProcessing());
        }
    }
}
