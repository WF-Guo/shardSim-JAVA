package edu.pku.infosec.transaction;

import java.util.ArrayList;
import java.util.List;

public class TxInfo {
    static long txCnt = 0;
    public final long id;
    public final List<TxInput> inputs;
    public int outputNum;

    public TxInfo() {
        this.id = txCnt++;
        this.inputs = new ArrayList<>();
    }
}
