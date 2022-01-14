package edu.pku.infosec.util;

import java.util.Random;

public class RandomQueue<T> {
    private final SplayTree splayTree = new SplayTree();
    private static final Random random = new Random();

    public void push(T data) {
        splayTree.pushBack(data);
    }

    public T pop() {
        return (T) splayTree.removeKth(random.nextInt(splayTree.size()) + 1);
    }

    public int size() {
        return splayTree.size();
    }
}
