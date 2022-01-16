package edu.pku.infosec.transaction;

public class TxInput {
    public final long tid;
    public final int n;

    public TxInput(long tid, int n) {
        this.tid = tid;
        this.n = n;
    }

    @Override
    public String toString() {
        return tid + ":" + n;
    }
}
