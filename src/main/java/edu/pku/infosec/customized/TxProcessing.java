package edu.pku.infosec.customized;

import edu.pku.infosec.event.EventHandler;
import edu.pku.infosec.event.EventParam;
import edu.pku.infosec.node.Node;

public class TxProcessing implements EventHandler {
    @Override
    public void run(Node currentNode, EventParam param) {
        /*
        Define your transaction processing rule here
        You can use the following to describe your model behaviour:
        1. currentNode.getId() returns the id of local node, which may be useful
        2. call TxStat.commit(tid) after it is safe for user to confirm that transaction
        3. Implement EventHandler and corresponding EventParam to define a new event type
        4. If you want local node to simulate a time-consuming task,
           call currentNode.stayBusy(time,eventHandler,eventParam) to lock the node and schedule what to do next.
           You can pack consecutive local tasks into a single one, until you need to send message to other nodes.
        5. To simulate sending message to another node, call currentNode.sendMessage(receiver,eventHandler,eventParam).
           It will calculate the transmission delay and let the receiving node run the eventHandler after the delay.
        6. To send message from node to external entities such as a client, call currentNode.sendOut.
           The eventHandler of this message will run on a special node with id=-1. You can use currentNode.sendIn
           to send message to a node on an external node, or use currentNode.stayBusy to delay that sending.
           Note that task conflict is ignored on an external node, so stayBusy won't get it really busy.
        7. If data structures is required for processing transactions, it is recommended that you define them as
           static fields in ModelData.
        8. If you need a coinbase tx, just create a transaction with no input and commit it, it will not be
           counted in throughput and latency
         */
    }
}
