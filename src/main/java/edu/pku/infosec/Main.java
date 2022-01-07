package edu.pku.infosec;

import edu.pku.infosec.customized.MyNetwork;
import edu.pku.infosec.event.EventDriver;
import edu.pku.infosec.node.Network;

public class Main {
    public static void main(String[] args) {
        // Todo: Read config from file
        Network network = new MyNetwork(5, true);
        network.configConnection();
        network.calcPath();
        // Todo: Set node info
        // Todo: Insert tx coming event
        EventDriver.start();
    }
}
