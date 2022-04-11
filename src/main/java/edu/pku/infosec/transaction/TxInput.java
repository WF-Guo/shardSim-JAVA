package edu.pku.infosec.transaction;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

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
        try {
            return Arrays.hashCode(MessageDigest.getInstance("md5").digest(toString().getBytes()));
        } catch (NoSuchAlgorithmException e) {
            return 0;
        }
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
