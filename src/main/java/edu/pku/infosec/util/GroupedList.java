package edu.pku.infosec.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class GroupedList<GroupKey, Value> {
    private final HashMap<GroupKey, List<Value>> groupMap = new HashMap<>();
    public void put(GroupKey key, Value value) {
        groupMap.putIfAbsent(key,new ArrayList<>());
        groupMap.get(key).add(value);
    }
    public List<Value> getGroup(GroupKey key) {
        groupMap.putIfAbsent(key,new ArrayList<>());
        return groupMap.get(key);
    }

    public void removeGroup(GroupKey key) {
        groupMap.remove(key);
    }
}
