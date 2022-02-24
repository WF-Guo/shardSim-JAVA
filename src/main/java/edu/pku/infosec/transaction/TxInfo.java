package edu.pku.infosec.transaction;

import edu.pku.infosec.event.EventParam;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class TxInfo extends EventParam {
    static long txCnt = 0;
    public final long id;
    public final List<TxInput> inputs;
    public int outputNum;

    public TxInfo() {
        this.id = txCnt++;
        this.inputs = new ArrayList<>();
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, inputs, outputNum);
    }
}
