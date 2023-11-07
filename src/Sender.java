package wifi;

import rf.RF;

public class Sender implements Runnable {
    private static final int IDLE_WAIT_TIME = 50;
    private RF rf;
    private Packet packet;

    /**
     * Constructor.
     */
    public Sender(RF rf, Packet packet) {
        this.rf = rf;
        this.packet = packet;
    }

    /**
     * Transmits the packet.
     */
    @Override
    public void run() {
        // Wait for channel to become idle
        while (this.rf.inUse()) {
            try {
                Thread.sleep(Sender.IDLE_WAIT_TIME);
            } catch (InterruptedException e) {}
        }

        // Transmit packet
        this.rf.transmit(packet.getBytes());
    }
}
