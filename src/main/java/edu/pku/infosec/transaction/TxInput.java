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

    @Override
    public int hashCode() {
        return (int) (tid * 8 + n);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TxInput input = (TxInput) o;

        if (tid != input.tid) return false;
        return n == input.n;
    }
}
