package wifi;

import java.io.PrintWriter;
import java.util.concurrent.LinkedBlockingQueue;

import rf.RF;

/**
 * Handles receiving packets and sending acknowledgements.
 */
public class Receiver implements Runnable {
    private RF rf;
    private LinkLayer linkLayer;
    private short mac;
    private Sender sender;
    private Clock clock;
    private boolean stop;
    private LinkedBlockingQueue<Packet> packetQueue;
    private Acknowledger acknowledger;
    private PrintWriter output;

    /**
     * Constructor.
     * @param rf The RF layer to listen and send on.
     */
    public Receiver(RF rf, LinkLayer linkLayer, short mac, Sender sender, Clock clock, PrintWriter output) {
        this.rf = rf;
        this.linkLayer = linkLayer;
        this.mac = mac;
        this.sender = sender;
        this.clock = clock;
        this.output = output;
        this.stop = false;
        this.packetQueue = new LinkedBlockingQueue<>();
        
        // Create acknowledger thread
        this.acknowledger = new Acknowledger(this.rf, this.output);
        new Thread(this.acknowledger).start();
    }

    /**
     * Listens for incoming packets.
     */
    @Override
    public void run() {
        while (!this.stop) {
            Packet packet = new Packet(this.rf.receive());
            if (packet.getFrameType() != Packet.FrameType.ACK) {
                if (this.linkLayer.getDebugLevel() == LinkLayer.DebugLevel.FULL) {
                    this.output.println("Received packet from " + packet.getSrcAddr());
                }
            } else {
                if (this.linkLayer.getDebugLevel() == LinkLayer.DebugLevel.FULL) {
                    this.output.println("Received ACK from " + packet.getSrcAddr());
                }
            }

            // Handle checksum :)
            if (!packet.checksumValid()) {
                if (this.linkLayer.getDebugLevel() == LinkLayer.DebugLevel.FULL) {
                    this.output.println("Broken packet received.");
                }
                continue;
            }

            // Ignore packets that aren't for this destination
            if (packet.isBroadcast() || packet.getDestAddr() == this.mac) {
                try {
                    if (packet.getFrameType() == Packet.FrameType.ACK) {
                        this.sender.setAcknowledgement(packet);
                    } else if (packet.getFrameType() == Packet.FrameType.BEACON) {
                        this.clock.receiveFrame(packet);
                    } else {
                        packetQueue.put(packet);

                        // Send acknowledgement
                        if (!packet.isBroadcast()) {
                            if (this.linkLayer.getDebugLevel() == LinkLayer.DebugLevel.FULL) {
                                this.output.println("Sending ACK " + packet.getFrameNumber() + " at " + clock.getTime());
                            }
                            Packet ack = new Packet(Packet.FrameType.ACK, false,
                                packet.getFrameNumber(), packet.getSrcAddr(), packet.getDestAddr(), new byte[0]);
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

    /**
     * Sleep for a specified amount of time.
     * @param ms The time to sleep.
     */
    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {}
    }
}
