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
        // Running on client
        TxInfo tx = new TxInfo();
        int inputNum = RandomChoose.chooseByDistribution(inputNumDistribution);
        int outputNum = RandomChoose.chooseByDistribution(outputNumDistribution);
        for (int i = 0; i < inputNum && TxStat.utxoSize() > 0; i++)
            tx.inputs.add(TxStat.getRandomUTXO());
        tx.setOutputNum(outputNum);
        TxStat.submit(tx);
        currentNode.sendIn(random.nextInt(ModelData.NODE_NUM), new TxProcessing(tx));
        if (random.nextDouble() < DSAttackRate && tx.inputs.size() > 0) {
            TxInfo attack = new TxInfo();
            attack.inputs.add(tx.inputs.get(0));
            for (int i = 1; i < inputNum && TxStat.utxoSize() > 0; i++)
                attack.inputs.add(TxStat.getRandomUTXO());
            attack.setOutputNum(outputNum);
            TxStat.submit(attack);
            currentNode.sendIn(random.nextInt(ModelData.NODE_NUM), new TxProcessing(attack));
        }
    }
}
