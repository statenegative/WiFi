package wifi;

import java.util.concurrent.LinkedBlockingQueue;

import rf.RF;

/**
 * Handles receiving packets and sending acknowledgements.
 */
public class Receiver implements Runnable {
    private static final short BROADCAST_ADDR = (short)0xFFFF;
    private RF rf;
    private short mac;
    private boolean stop;
    private LinkedBlockingQueue<Packet> packetQueue;

    /**
     * Constructor.
     * @param rf The RF layer to listen and send on.
     */
    public Receiver(RF rf, short mac) {
        this.rf = rf;
        this.mac = mac;
        this.packetQueue = new LinkedBlockingQueue<>();
        this.stop = false;
    }

    /**
     * Listens for incoming packets.
     */
    @Override
    public void run() {
        while (!this.stop) {
            Packet packet = new Packet(this.rf.receive());

            // Ignore packets that aren't for this destination
            if (packet.getDestAddr() == Receiver.BROADCAST_ADDR || packet.getDestAddr() == this.mac) {
                try {
                    packetQueue.put(packet);
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
    }
}
