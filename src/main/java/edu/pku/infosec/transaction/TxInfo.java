package edu.pku.infosec.transaction;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class TxInfo {
    static long txCnt = 0;
    public final long id;
    public final List<TxInput> inputs;
    public final List<TxInput> outputs;

    public TxInfo() {
        this.id = txCnt++;
        this.inputs = new ArrayList<>();
        this.outputs = new ArrayList<>();
    }

    public void setOutputNum(int n) {
        for (int i = 0; i < n; i++)
            outputs.add(new TxInput(id, i));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TxInfo txInfo = (TxInfo) o;

        return id == txInfo.id;
    }

    @Override
    public int hashCode() {
        return (int) (id ^ (id >>> 32));
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, inputs, outputNum);
    }
}
