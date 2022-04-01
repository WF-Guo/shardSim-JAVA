package edu.pku.infosec.util;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

public class GroupedSet<GroupKey, Value> {
    private final HashMap<GroupKey, Set<Value>> groupMap = new HashMap<>();

    public void put(GroupKey key, Value value) {
        groupMap.putIfAbsent(key,new HashSet<>());
        groupMap.get(key).add(value);
    }

    public Set<Value> getGroup(GroupKey key) {
        groupMap.putIfAbsent(key,new HashSet<>());
        return groupMap.get(key);
    }

    public void removeGroup(GroupKey key) {
        groupMap.remove(key);
    }
}
