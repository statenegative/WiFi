package wifi;

import java.nio.ByteBuffer;

import rf.RF;

/**
 * Clock.
 */
public class Clock {
    private RF rf;
    private long beaconInterval;
    private short mac;
    // Offset for synchronization purposes.
    private long offset;
    private boolean stop;
    // Previous beacon timestamp.
    private long lastTimestamp;
    // Beacon frame number.
    private short frameNumber;

    /**
     * Constructor.
     * @param rf The RF layer to listen and send on.
     */
    public Clock(RF rf, long beaconInterval, short mac) {
        this.rf = rf;
        this.beaconInterval = beaconInterval;
        this.mac = mac;
        this.offset = 0;
        this.stop = false;
        this.lastTimestamp = 0;
        this.frameNumber = 0;
    }

    public void updateInterval(long beaconInterval) {
        this.beaconInterval = beaconInterval;
    }

    /**
     * Updates the timestamp from another beacon frame.
     */
    public void receiveFrame(Packet beacon) {
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        buffer.put(beacon.getData());
        buffer.flip();
        long timestamp = buffer.getLong();
        
        // Update offset
        if (timestamp > this.getTime()) {
            this.offset += timestamp - this.getTime();
            System.out.println("Timestamp increased by " + this.offset);
        }
    }

    /**
     * Gets the current synchronized time.
     */
    public long getTime() {
        return this.rf.clock() + this.offset;
    }

    /**
     * Kills this thread.
     */
    public void stop() {
        this.stop = true;
    }

    /**
     * @return Whether a beacon frame is ready to be transmitted.
     */
    public boolean frameReady() {
        return this.getTime() >= this.lastTimestamp + this.beaconInterval;
    }

    /**
     * @return The current beacon frame, or null if it's already been retrieved.
     */
    public Packet getFrame() {
        Packet beacon = null;
        if (this.frameReady()) {
            ByteBuffer timestamp = ByteBuffer.allocate(Long.BYTES);
            timestamp.putLong(this.getTime());
            beacon = new Packet(Packet.FrameType.BEACON, false, this.frameNumber, (short)-1, this.mac, timestamp.array());
            this.frameNumber++;
            this.lastTimestamp = this.getTime() - this.getTime() % this.beaconInterval;
        }

        return beacon;
    }
}
