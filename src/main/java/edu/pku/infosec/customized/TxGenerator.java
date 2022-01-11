package edu.pku.infosec.customized;

import com.alibaba.fastjson.JSONObject;
import edu.pku.infosec.event.EventHandler;
import edu.pku.infosec.event.EventParam;
import edu.pku.infosec.node.Node;
import edu.pku.infosec.transaction.TxInfo;
import edu.pku.infosec.transaction.TxStat;

import java.util.Random;

public class TxGenerator implements EventHandler {
    private static final Random random = new Random();
    private final JSONObject inputNumDistribution;
    private final JSONObject outputNumDistribution;
    double DSAttackRate;

    public TxGenerator(JSONObject txConfig) {
        DSAttackRate = txConfig.getDouble("DSAttackRate");
        inputNumDistribution = txConfig.getJSONObject("inputNum");
        outputNumDistribution = txConfig.getJSONObject("outputNum");
    }

    private static int chooseByDistribution(JSONObject distribution) {
        double v = random.nextDouble();
        for (String s : distribution.keySet()) {
            v -= Double.parseDouble(s);
            if (v <= 0)
                return distribution.getInteger(s);
        }
        return 0; // not gonna happen
    }

    @Override
    public void run(Node currentNode, EventParam param) {
        TxInfo tx = new TxInfo();
        int inputNum = chooseByDistribution(inputNumDistribution);
        tx.outputNum = chooseByDistribution(outputNumDistribution);
        for (int j = 0; j < inputNum; j++)
            tx.inputs.add(TxStat.getRandomUTXO()); // todo: double spending
    }
}
