package wifi;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.HashSet;

import rf.RF;

/**
 * Handles sending packets.
 */
public class Sender implements Runnable {
    // The time we wait between checking if the channel has become idle
    private static final int IDLE_WAIT_TIME = 50;
    // The time we wait between checking if an acknowledgement has arrived
    private static final int ACK_WAIT_TIME = 50;
    // The total time we wait for an acknowledgement to arrive before it times out
    private static final int TIMEOUT_DURATION = 5000;

    private RF rf;
    private LinkedBlockingQueue<Packet> packetQueue;
    private boolean stop;
    private Packet ack;

    /**
     * Constructor.
     * @param rf The RF layer to send packets on.
     */
    public Sender(RF rf) {
        this.rf = rf;
        this.packetQueue = new LinkedBlockingQueue<>();
        this.stop = false;
        this.ack = null;
    }

    /**
     * Sends packets as they arrive.
     */
    @Override
    public void run() {
        while (!this.stop) {
            // Check for packet
            Packet packet = null;
            try {
                packet = this.packetQueue.take();
            } catch (InterruptedException e) {}

            // Transmit packet
            if (packet != null) {
                // Wait for channel to become idle
                while (this.rf.inUse()) {
                    try {
                        Thread.sleep(Sender.IDLE_WAIT_TIME);
                    } catch (InterruptedException e) {}
                }

                // Rese the acknowledgement to null
                this.ack = null;

                // Transmit the packet and start timeout counter
                this.rf.transmit(packet.getBytes());
                long timeout = this.rf.clock() + Sender.TIMEOUT_DURATION;

                // Wait for acknowledgement to arrive
                if (!packet.isBroadcast()) {
                    while (this.ack == null && this.rf.clock() < timeout) {
                        try {
                            Thread.sleep(Sender.ACK_WAIT_TIME);
                        } catch (InterruptedException e) {}
                    }

                    // Determine whether acknowledgement arrived
                    if (this.ack == null) {
                        // Handle timeout
                        System.err.println("Acknowledgement timed out.");
                    } else {
                        // Else is just for debugging
                        System.err.println("Acknowledgement received.");
                    }
                }
            }
        }
    }

    /**
     * Queues a packet to be sent.
     * @param packet The packet to send.
     */
    public void send(Packet packet) {
        try {
            this.packetQueue.put(packet);
        } catch (InterruptedException e) {}
    }

    /**
     * Transmits an acknowledgement.
     * @param ack The acknowledgement packet.
     */
    public void setAcknowledgement(Packet ack) {
        this.ack = ack;
    }

    /**
     * Kills this thread.
     */
    public void stop() {
        this.stop = true;
    }
}
