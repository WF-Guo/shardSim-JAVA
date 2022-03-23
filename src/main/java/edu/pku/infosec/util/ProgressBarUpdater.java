package edu.pku.infosec.util;

import edu.pku.infosec.transaction.TxStat;

import javax.swing.*;
import java.util.TimerTask;

public class ProgressBarUpdater extends TimerTask {
    final int full;
    public ProgressBarUpdater(int transactionNum) {
        full = transactionNum;
    }

    @Override
    public void run() {
        System.err.println(TxStat.processedNum() + "/" + full);
    }
}
