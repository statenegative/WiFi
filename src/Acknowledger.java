package wifi;

import java.io.PrintWriter;
import java.util.concurrent.LinkedBlockingQueue;

import rf.RF;

public class Acknowledger implements Runnable {
    private static final long IDLE_WAIT_TIME = 50;
    private RF rf;
    private LinkedBlockingQueue<Packet> packetQueue;
    private boolean stop;
    private PrintWriter output;

    /**
     * Constructor.
     */
    public Acknowledger(RF rf, PrintWriter output) {
        this.rf = rf;
        this.output = output;
        this.packetQueue = new LinkedBlockingQueue<>();
        this.stop = false;
    }

    /**
     * Transmits the acknowledgement.
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
                // Wait SIFS
                this.sleep(this.rf.aSIFSTime);

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
