package edu.pku.infosec.customized;

import com.alibaba.fastjson.JSONObject;
import edu.pku.infosec.event.EventHandler;
import edu.pku.infosec.event.EventParam;
import edu.pku.infosec.node.Node;
import edu.pku.infosec.transaction.TxInfo;
import edu.pku.infosec.transaction.TxStat;

import edu.pku.infosec.util.RandomChoose;

public class TxGenerator implements EventHandler {
    private final JSONObject inputNumDistribution;
    private final JSONObject outputNumDistribution;
    double DSAttackRate;

    public TxGenerator(JSONObject txConfig) {
        DSAttackRate = txConfig.getDouble("DSAttackRate");
        inputNumDistribution = txConfig.getJSONObject("inputNum");
        outputNumDistribution = txConfig.getJSONObject("outputNum");
    }

    @Override
    public void run(Node currentNode, EventParam param) {
        TxInfo tx = new TxInfo();
        int inputNum = RandomChoose.chooseByDistribution(inputNumDistribution);
        tx.outputNum = RandomChoose.chooseByDistribution(outputNumDistribution);
        for (int j = 0; j < inputNum; j++)
            tx.inputs.add(TxStat.getRandomUTXO()); // todo: double spending
    }
}
