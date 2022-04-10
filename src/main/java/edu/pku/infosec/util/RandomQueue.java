package edu.pku.infosec.util;

import java.util.Random;

public class RandomQueue<T> {
    private final SplayTree splayTree = new SplayTree();
    private static final Random random = new Random(1453);

    public void add(T data) {
        splayTree.pushBack(data);
    }

    public T remove() {
        return (T) splayTree.removeKth(random.nextInt(splayTree.size()) + 1);
    }

    public int size() {
        return splayTree.size();
    }
}
