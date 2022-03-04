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
}
