package wifi;

import java.io.PrintWriter;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.HashSet;
import java.util.Random;

import rf.RF;

/**
 * Handles sending packets.
 */
public class Sender implements Runnable {
    // The time we wait between checking if the channel has become idle
    private static final long IDLE_WAIT_TIME = 50;
    // The time we wait between checking if an acknowledgement has arrived
    private static final long ACK_WAIT_TIME = 50;
    // The total time we wait for an acknowledgement to arrive before it times out
    private static final long TIMEOUT_DURATION = 5000;
    // DIFS wait time
    private final long DIFS;
    // Output stream to write to
    private PrintWriter output;

    private RF rf;
    private LinkedBlockingQueue<Packet> packetQueue;
    private boolean stop;
    private Packet ack;

    /**
     * Constructor.
     * @param rf The RF layer to send packets on.
     */
    public Sender(RF rf, PrintWriter output) {
        this.rf = rf;
        this.output = output;
        this.packetQueue = new LinkedBlockingQueue<>();
        this.stop = false;
        this.ack = null;

        // Define wait time value
        this.DIFS = this.rf.aSIFSTime + 2 * this.rf.aSlotTime;
    }

    /**
     * Sends packets as they arrive.
     */
    @Override
    public void run() {
        int retryCount = 0;

        while (!this.stop) {
            // Check for packet
            Packet packet = null;
            try {
                packet = this.packetQueue.take();
            } catch (InterruptedException e) {}

            // Set initial contention window
            int cw = this.rf.aCWmin;

            // Transmit packet
            boolean transmitted;
            do {
                // Check whether channel is busy
                this.output.println("Begin waiting for channel to be free.");
                boolean busy = this.busyWait();

                // Do exponential backoff
                if (busy) {
                    for (int count = new Random().nextInt(cw + 1); count > 0; count--) {
                        this.output.println("Began slot waiting.");
                        // Slot wait
                        this.sleep(this.rf.aSlotTime);

                        // Wait DIFS if channel is busy
                        if (this.rf.inUse()) {
                            this.busyWait();
                        }
                    }
                }

                // Transmit packet
                this.output.println("Sending packet " + packet.getFrameNumber() + "...");
                this.output.flush();
                transmitted = this.transmit(packet);

                // If acknowledgement wasn't received
                if (!transmitted) {
                    this.output.println("Timed out. Resending.");

                    // Rebuild packet with retransmission bit
                    packet = new Packet(packet.getFrameType(), true, packet.getFrameNumber(),
                        packet.getDestAddr(), packet.getSrcAddr(), packet.getData());

                    // Double contention window
                    cw *= 2;
                    if (cw > this.rf.aCWmax) {
                        cw = this.rf.aCWmax;
                    }

                    // Increment retry counter
                    retryCount++;
                } else {
                    this.output.println("ACK received.");
                }
            } while (!transmitted && retryCount < this.rf.dot11RetryLimit);
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

    /**
     * Transmits packet and waits for acknowledgement.
     * @return Whether the packet was successfully transmitted.
     */
    private boolean transmit(Packet packet) {
        // Reset the acknowledgement to null
        this.ack = null;

        // Transmit the packet and start timeout counter
        this.rf.transmit(packet.getBytes());
        long timeout = this.rf.clock() + Sender.TIMEOUT_DURATION;

        // Wait for acknowledgement to arrive
        if (!packet.isBroadcast()) {
            while (this.ack == null && this.rf.clock() < timeout) {
                this.sleep(Sender.ACK_WAIT_TIME);
            }
        }

        // Return whether the packet was successfully transmitted
        return packet.isBroadcast() || this.ack != null;
    }

    /**
     * Wait for the channel to become idle, waiting DIFS before rechecking.
     * @return Whether the channel was busy at some point while waiting. 
     */
    private boolean busyWait() {
        // Wait until current transmission ends
        boolean busy = false;
        boolean idle;
        do {
            this.output.println("Waited in busyWait.");
            // Wait for idle
            while (this.rf.inUse()) {
                busy = true;
                this.sleep(Sender.IDLE_WAIT_TIME);
            }

            // Wait DIFS
            this.sleep(this.DIFS);

            // Check if still idle
            idle = !this.rf.inUse();
        } while (!idle);

        return busy;
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
