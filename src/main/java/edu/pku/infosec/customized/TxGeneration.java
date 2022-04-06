package edu.pku.infosec.customized;

import com.alibaba.fastjson.JSONObject;
import edu.pku.infosec.event.NodeAction;
import edu.pku.infosec.node.Node;
import edu.pku.infosec.transaction.TxInfo;
import edu.pku.infosec.transaction.TxStat;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Random;

public class TxGeneration implements NodeAction {
    private final Random random = new Random();
    private final ArrayList<DistributionEntry> distributionList = new ArrayList<>();
    private final double freqSum;
    private final double DSAttackRate;

    public TxGeneration(JSONObject txConfig) {
        DSAttackRate = txConfig.getDouble("DSAttackRate");
        double freqSum1 = 0;
        try {
            final BufferedReader reader = new BufferedReader(new FileReader(txConfig.getString("distribution")));
            String csvLine;
            while ((csvLine = reader.readLine()) != null) {
                final DistributionEntry distributionEntry = new DistributionEntry(csvLine);
                freqSum1 += distributionEntry.freq;
                distributionEntry.freqPrefixSum = freqSum1;
                distributionList.add(distributionEntry);
            }
        } catch (FileNotFoundException e) {
            throw new RuntimeException("Distribution File Not Found");
        } catch (IOException e) {
            throw new RuntimeException("Failed to read distribution");
        }
        freqSum = freqSum1;
    }

    private DistributionEntry getRandomEntryByDistribution() {
        final double v = random.nextDouble() * freqSum;
        int l = 0, r = distributionList.size() - 1;
        while (l < r) {
            final int mid = (l + r) / 2;
            final DistributionEntry entry = distributionList.get(mid);
            if (entry.freqPrefixSum < v)
                l = mid + 1;
            else r = mid;
        }
        return distributionList.get(l);
    }

    @Override
    public void runOn(Node currentNode) {
        // Running on client
        final TxInfo tx = new TxInfo();
        final DistributionEntry entry = getRandomEntryByDistribution();
        for (int i = 0; i < entry.inputNum && TxStat.utxoSize() > 0; i++)
            tx.inputs.add(TxStat.getRandomUTXO());
        tx.setOutputNum(entry.outputNum);
        TxStat.submit(tx);
        currentNode.sendIn(random.nextInt(ModelData.nodeNum), new TxProcessing(tx));
        if (random.nextDouble() < DSAttackRate && tx.inputs.size() > 0) {
            final TxInfo attack = new TxInfo();
            attack.inputs.add(tx.inputs.get(0));
            for (int i = 1; i < entry.inputNum && TxStat.utxoSize() > 0; i++)
                attack.inputs.add(TxStat.getRandomUTXO());
            attack.setOutputNum(entry.outputNum);
            TxStat.submit(attack);
            currentNode.sendIn(random.nextInt(ModelData.nodeNum), new TxProcessing(attack));
        }
    }
}

class DistributionEntry {
    public final int inputNum;
    public final int outputNum;
    public final double freq;
    public double freqPrefixSum;

    public DistributionEntry(String csvLine) {
        final String[] strings = csvLine.split(",");
        this.inputNum = Integer.parseInt(strings[0]);
        this.outputNum = Integer.parseInt(strings[1]);
        this.freq = Double.parseDouble(strings[2]);
    }
}