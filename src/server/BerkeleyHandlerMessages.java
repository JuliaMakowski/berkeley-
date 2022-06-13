package server;

import domain.Clock;
import domain.Message;
import domain.MessageTypes;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class BerkeleyHandlerMessages implements Consumer<Message> {
    private Map<NodeReference, NodeReferenceTime> clockNodes;
    private DatagramSocket socket;
    private int processTime;
    private Clock clock;
    private int nodesNumber;

    private BerkeleyAlg berkeleyAlg;
    private long timeSent;

    public BerkeleyHandlerMessages(DatagramSocket socket, int startNodesNumber, Clock clock, int processTime, BerkeleyAlg berkeleyAlg) {
        this.clockNodes = new HashMap<>();
        this.socket = socket;
        this.nodesNumber = startNodesNumber;
        this.clock = clock;
        this.timeSent = 0;
        this.berkeleyAlg = berkeleyAlg;
        this.processTime = processTime;
    }

    public void setTimeSent(long time) {
        this.timeSent = time;
    }

    public void increaseNodeNumber() {
        nodesNumber++;
    }

    @Override
    public void accept(Message message) {
        String payload = message.getPayload();
        String[] messageParts = payload.split(";");
        System.out.println("Received message from process: " + messageParts[1] + " : " + payload);
        if (MessageTypes.SEND_CLOCK.name().equals(messageParts[0])) {
            NodeReference reference = new NodeReference(message.getFrom(), message.getPort(), messageParts[1]);
            NodeReferenceTime referenceTime = new NodeReferenceTime(LocalTime.parse(messageParts[2]), clock.timeOnMs());
            clockNodes.put(reference, referenceTime);
        }
        if (clockNodes.size() >= nodesNumber) {
            cleanMapAndExecute();
        }
    }

    private void cleanMapAndExecute() {
        Map<NodeReference, NodeReferenceTime> nodes = new HashMap<>(this.clockNodes);
        this.clockNodes.clear();
        Map<String, NodeReferenceTime> nodeReferenceTimeMap = nodes.entrySet()
                .stream().collect(Collectors.toMap(e -> e.getKey().getId(), Map.Entry::getValue));
        nodeReferenceTimeMap.put("SERVER", new NodeReferenceTime(clock.getTime(), 0L));
        Map<String, Long> results = berkeleyAlg.runAlg(nodeReferenceTimeMap, timeSent, processTime);
        try {
            Thread.sleep(processTime);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        results.forEach((key, value) -> {
            if (key.equals("SERVER")) {
                changeCurrent(value);
            } else {
                NodeReference reference = nodes.keySet().stream().filter(it -> it.getId().equals(key))
                        .findFirst().get();
                adjustClock(reference, value);
            }
        });
    }

    private void adjustClock(NodeReference nodeReference, Long time) {
        try {
            String operation = time < 0 ? "minus" : "plus";
            String msg = MessageTypes.FIX_CLOCK.name() + ";" + operation + ";" + Math.abs(time);
            byte[] byteMessage = msg.getBytes();
            System.out.println("Sending " + nodeReference.getId() + " message: " + msg);
            DatagramPacket packet = new DatagramPacket(byteMessage, byteMessage.length, nodeReference.getAddress(), nodeReference.getPort());
            socket.send(packet);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void changeCurrent(long toChange) {
        if (toChange < 0) {
            clock.decrease(Math.abs(toChange));
        } else {
            clock.increase(Math.abs(toChange));
        }
    }
}
