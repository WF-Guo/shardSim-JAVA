package edu.pku.infosec.util;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class Counter<T> {
    private final Map<T, Integer> map;

    public Counter() {
        this.map = new HashMap<>();
    }

    public void add(T item, int number) {
        map.merge(item, number, Integer::sum);
    }

    public void add(T item) {
        add(item, 1);
    }

    public int count(T item) {
        return map.getOrDefault(item, 0);
    }

    public Set<T> keySet() {
        return map.keySet();
    }
}
