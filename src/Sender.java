package wifi;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import rf.RF;

/**
 * Handles sending packets.
 */
public class Sender implements Runnable {
    private static final int IDLE_WAIT_TIME = 50;
    private static final int PACKET_WAIT_TIME = 50;
    private RF rf;
    private LinkedBlockingQueue<Packet> packetQueue;
    private boolean stop;

    /**
     * Constructor.
     * @param rf The RF layer to send packets on.
     */
    public Sender(RF rf) {
        this.rf = rf;
        this.packetQueue = new LinkedBlockingQueue<>();
        this.stop = false;
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
                packet = this.packetQueue.poll(Sender.PACKET_WAIT_TIME, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {}

            // Transmit packet
            if (packet != null) {
                // Wait for channel to become idle
                while (this.rf.inUse()) {
                    try {
                        Thread.sleep(Sender.IDLE_WAIT_TIME);
                    } catch (InterruptedException e) {}
                }

                this.rf.transmit(packet.getBytes());
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
     * Kills this thread.
     */
    public void stop() {
        this.stop = true;
    }
}
