package wifi;

import java.util.concurrent.LinkedBlockingQueue;

import rf.RF;

/**
 * Handles receiving packets and sending acknowledgements.
 */
public class Receiver implements Runnable {
    private RF rf;
    private short mac;
    private Sender sender;
    private boolean stop;
    private LinkedBlockingQueue<Packet> packetQueue;
    private Acknowledger acknowledger;

    /**
     * Constructor.
     * @param rf The RF layer to listen and send on.
     */
    public Receiver(RF rf, short mac, Sender sender) {
        this.rf = rf;
        this.mac = mac;
        this.sender = sender;
        this.stop = false;
        this.packetQueue = new LinkedBlockingQueue<>();
        
        // Create acknowledger thread
        this.acknowledger = new Acknowledger(this.rf);
        new Thread(this.acknowledger).start();
    }

    /**
     * Listens for incoming packets.
     */
    @Override
    public void run() {
        while (!this.stop) {
            Packet packet = new Packet(this.rf.receive());

            // Ignore packets that aren't for this destination
            if (packet.isBroadcast() || packet.getDestAddr() == this.mac) {
                try {
                    if (packet.getFrameType() == Packet.FrameType.ACK) {
                        this.sender.setAcknowledgement(packet);
                    } else {
                        packetQueue.put(packet);

                        // Send acknowledgement
                        if (!packet.isBroadcast()) {
                            Packet ack = new Packet(Packet.FrameType.ACK, false,
                                packet.getFrameNumber(), packet.getSrcAddr(), packet.getDestAddr(), new byte[0], -1);
                            this.acknowledger.send(ack);
                        }
                    }
                } catch (InterruptedException e) {}
            }
        }
    }

    /**
     * Blocks until a packet is received, at which point that packet is returned.
     * ACKs are processed separately and won't be returned by this method.
     * @return A received packet.
     */
    public Packet recv() {
        try {
            return this.packetQueue.take();
        } catch (InterruptedException e) {
            return null;
        }
    }

    /**
     * Kills this thread.
     */
    public void stop() {
        this.stop = true;
        this.acknowledger.stop();
    }
}
