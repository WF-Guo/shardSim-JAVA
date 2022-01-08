package edu.pku.infosec.transaction;

import edu.pku.infosec.event.EventParam;

import java.util.ArrayList;
import java.util.List;

public class TxInfo extends EventParam {
    static long txCnt = 0;
    public final long id;
    public final List<TxInput> inputs;
    public final List<String> outputs;

    public TxInfo() {
        this.id = txCnt++;
        this.inputs = new ArrayList<>();
        this.outputs = new ArrayList<>();
    }
}
