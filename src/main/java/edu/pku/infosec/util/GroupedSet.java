package edu.pku.infosec.util;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

public class GroupedSet<GroupKey, Value> {
    private final HashMap<GroupKey, Set<Value>> groupMap = new HashMap<>();

    public void put(GroupKey key, Value value) {
        if (!groupMap.containsKey(key))
            groupMap.put(key, new HashSet<>());
        groupMap.get(key).add(value);
    }

    public Set<Value> getGroup(GroupKey key) {
        return groupMap.getOrDefault(key, new HashSet<>());
    }


    public void removeGroup(GroupKey key) {
        groupMap.remove(key);
    }
}
