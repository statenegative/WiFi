package wifi;

import java.io.PrintWriter;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
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
    private final long TIMEOUT_DURATION;
    // DIFS wait time
    private final long DIFS;
    // Output stream to write to
    private PrintWriter output;
    // Whether slot selection is fixed or random
    boolean randomSlotSelection;
    
    private RF rf;
    private LinkLayer linkLayer;
    private Clock clock;
    private LinkedBlockingQueue<Packet> packetQueue;
    private boolean stop;
    private Packet ack;
    private Packet beacon;

    /**
     * Constructor.
     * @param rf The RF layer to send packets on.
     */
    public Sender(RF rf, LinkLayer linkLayer, Clock clock, PrintWriter output) {
        this.rf = rf;
        this.linkLayer = linkLayer;
        this.clock = clock;
        this.output = output;
        this.packetQueue = new LinkedBlockingQueue<>();
        this.stop = false;
        this.ack = null;
        this.beacon = null;
        this.randomSlotSelection = true;

        // Define wait time value
        this.DIFS = this.rf.aSIFSTime + 2 * this.rf.aSlotTime;

        // Define timeout value
        this.TIMEOUT_DURATION = 7_500 + this.rf.aSlotTime;
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

            // Get beacon frame if ready
            if (this.clock.frameReady()) {
                packet = this.clock.getFrame();
            } else {
                try {
                    packet = this.packetQueue.poll(50, TimeUnit.MILLISECONDS);
                    // :)
                    if (packet == null) {
                        continue;
                    }
                } catch (InterruptedException e) {}
            }

            // Set initial contention window
            int cw = this.rf.aCWmin + 1;

            // Transmit packet
            boolean transmitted;
            do {
                // Check whether channel is busy
                boolean busy = this.busyWait();

                // Do exponential backoff
                if (busy) {
                    // Select slot wait time
                    int count;
                    if (this.randomSlotSelection) {
                        count = new Random().nextInt(cw);
                    } else {
                        count = cw - 1;
                    }

                    if (this.linkLayer.getDebugLevel() == LinkLayer.DebugLevel.FULL) {
                        this.output.println("Busy waiting: selected wait time of " + count + " from [0-" + (cw - 1) + "]");
                    }

                    for (; count > 0; count--) {
                        // Slot wait
                        this.sleep(this.rf.aSlotTime);

                        // Wait DIFS if channel is busy
                        if (this.rf.inUse()) {
                            this.busyWait();
                        }
                    }
                }

                // Transmit packet
                transmitted = this.transmit(packet);

                // If acknowledgement wasn't received
                if (!transmitted) {
                    if (this.linkLayer.getDebugLevel() == LinkLayer.DebugLevel.FULL) {
                        this.output.println("Packet #" + packet.getFrameNumber() + " timed out at " + this.clock.getTime());
                    }

                    // Rebuild packet with retransmission bit
                    packet = new Packet(packet.getFrameType(), true, packet.getFrameNumber(),
                        packet.getDestAddr(), packet.getSrcAddr(), packet.getData());

                    // Double contention window
                    cw *= 2;
                    if (cw > this.rf.aCWmax + 1) {
                        cw = this.rf.aCWmax + 1;
                    }

                    // Increment retry counter
                    retryCount++;
                }
            } while (!transmitted && retryCount < this.rf.dot11RetryLimit);

            // Set status
            if (transmitted) {
                this.linkLayer.setStatus(LinkLayer.Status.TX_DELIVERED);
            } else {
                if (this.linkLayer.getDebugLevel() == LinkLayer.DebugLevel.FULL) {
                    this.output.println("Packet #" + packet.getFrameNumber() + " failed to send.");
                }
                this.linkLayer.setStatus(LinkLayer.Status.TX_FAILED);
            }
        }
    }

    /**
     * Queues a packet to be sent.
     * @param packet The packet to send.
     * @return Whether the packet was successfully queued to be sent.
     */
    public boolean send(Packet packet) {
        if (this.packetQueue.size() >= 4) {
            return false;
        }

        try {
            this.packetQueue.put(packet);
        } catch (InterruptedException e) {}

        return true;
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

    public void setSlotSelection(boolean randomSlotSelection) {
        this.randomSlotSelection = randomSlotSelection;
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
        long timeout = this.clock.getTime() + this.TIMEOUT_DURATION;

        // Wait for acknowledgement to arrive
        if (!packet.isBroadcast()) {
            while (this.ack == null && this.clock.getTime() < timeout) {
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
            // Wait for idle
            while (this.rf.inUse()) {
                busy = true;
                this.sleep(Sender.IDLE_WAIT_TIME);
            }

            // Wait DIFS (rounded up to 50ms boundaries)
            long currTime = this.clock.getTime() % 50;
            long waitTime = this.DIFS + (50 - currTime);
            this.sleep(waitTime);

            if (this.linkLayer.getDebugLevel() == LinkLayer.DebugLevel.FULL) {
                this.output.println("DIFS wait finished at " + this.clock.getTime());
            }

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
