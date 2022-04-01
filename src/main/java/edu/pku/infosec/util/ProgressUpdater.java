package edu.pku.infosec.util;

import edu.pku.infosec.event.EventDriver;
import edu.pku.infosec.transaction.TxStat;

import java.util.TimerTask;

public class ProgressUpdater extends TimerTask {
    final int full;
    public ProgressUpdater(int transactionNum) {
        full = transactionNum;
    }

    @Override
    public void run() {
        System.err.println(TxStat.processedNum() + "/" + full + " , Time = " + EventDriver.getCurrentTime());
    }
}
