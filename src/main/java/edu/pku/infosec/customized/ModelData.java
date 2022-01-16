package edu.pku.infosec.customized;

import edu.pku.infosec.transaction.TxInput;

import java.util.HashSet;
import java.util.Set;

public class ModelData {
    public static int nodeNum;
    public static Set<TxInput> utxoSet = new HashSet<>();

    // addInitUTXO will be called at system initialization, and never later
    public static void addInitUTXO(TxInput utxo) {
        utxoSet.add(utxo);
    }
}
