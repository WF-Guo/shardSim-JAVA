package edu.pku.infosec.event;

import edu.pku.infosec.node.Node;

public interface NodeAction {
    void runOn(Node currentNode);
}

