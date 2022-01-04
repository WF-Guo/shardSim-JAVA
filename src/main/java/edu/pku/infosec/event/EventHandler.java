package edu.pku.infosec.event;

import edu.pku.infosec.node.Node;

public interface EventHandler {
    void run(Node currentNode, EventParam param);
}
