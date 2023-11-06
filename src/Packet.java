package wifi;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Optional;
import java.util.NoSuchElementException;

/**
 * 802.11~ packet.
 */
public class Packet {
    private FrameType type;
    private boolean retransmission;
    private short frameNumber;
    private short destAddr;
    private short srcAddr;
    private byte[] data;
    private int crc;
    private ByteBuffer frame;

    /** Describes the packet's frame type. */
    public enum FrameType {
        DATA(0b000, "DATA"),
        ACK(0b001, "ACK"),
        BEACON(0b010, "BEACON"),
        CTS(0b100, "CTS"),
        RTS(0b101, "RTS");

        private final int value;
        private final String name;

        public static Optional<FrameType> valueOf(int value) {
            return Arrays.stream(values()).filter(type -> type.value == value).findFirst();
        }

        @Override
        public String toString() {
            return this.name;
        }

        private FrameType(int value, String name) {
            this.value = value;
            this.name = name;
        }
    }

    /**
     * Outgoing packet constructor.
     * @param type The type of the frame.
     * @param retransmission Whether this frame is a retransmission.
     * @param frameNumber Number used to distinguish and order frames.
     * @param destAddr Destination MAC address.
     * @param srcAddr Source MAC address.
     * @param data Data being sent in the packet.
     * @param crc CRC checksum.
     */
    public Packet(FrameType type, boolean retransmission, short frameNumber, short destAddr, short srcAddr, byte[] data, int crc) {
        this.type = type;
        this.retransmission = retransmission;
        this.frameNumber = frameNumber;
        this.destAddr = destAddr;
        this.srcAddr = srcAddr;
        this.data = data.clone();

        // Build frame
        short control = Packet.buildControlField(type, retransmission, frameNumber);
        this.frame = ByteBuffer.allocate(10 + data.length);
        this.frame.putShort(control);
        this.frame.putShort(destAddr);
        this.frame.putShort(srcAddr);
        this.frame.put(data);
        this.frame.putInt(crc);
    }

    /**
     * Incoming packet constructor.
     * @param frame Raw data of the frame.
     */
    public Packet(byte[] frame) throws NoSuchElementException {
        this.frame = ByteBuffer.wrap(frame);

        // Parse frame
        short control = this.frame.getShort();
        this.destAddr = this.frame.getShort();
        this.srcAddr = this.frame.getShort();
        this.data = new byte[frame.length - 10];
        this.frame.get(this.data);
        this.crc = this.frame.getInt();

        // Parse control field
        this.type = Packet.getFrameType(control);
        this.retransmission = Packet.getRetransmission(control);
        this.frameNumber = Packet.getFrameNumber(control);
    }

    /**
     * destAddr getter.
     * @return destAddr.
     */
    public short getDestAddr() {
        return this.destAddr;
    }

    /**
     * srcAddr getter.
     * @return srcAddr.
     */
    public short getSrcAddr() {
        return this.srcAddr;
    }

    /**
     * data getter.
     * @return data.
     */
    public byte[] getData() {
        return this.data;
    }

    /**
     * Gets the byte representation of the entire packet.
     * @return The packet's bytes.
     */
    public byte[] getBytes() {
        return this.frame.array();
    }

    /**
     * Creates a string representation of the packet.
     * @return String representation of the packet.
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("[[");
        sb.append(this.type + ", ");
        sb.append(this.retransmission + ", ");
        sb.append(this.frameNumber + "], [");
        sb.append(this.destAddr + ", ");
        sb.append(this.srcAddr + "], [");
        sb.append(this.data.length + "b...], ");
        sb.append(this.crc + "]");
        return sb.toString();
    }

    /**
     * Control field constructor.
     * @param type The type of the frame.
     * @param retransmission Whether this frame is a retransmission.
     * @param frameNumber Number used to distinguish and order frames.
     * @return The constructed control field.
     */
    private static short buildControlField(FrameType type, boolean retransmission, short frameNumber) {
        int typeBits = type.value;
        int rtBit = (retransmission ? 1 : 0) & 0b1 << 3;
        int frameNumBits = frameNumber << 4;
        return (short)(typeBits + rtBit + frameNumBits);
    }

    /**
     * Gets the frame type from a control field.
     * @param control The control field to read from.
     * @return The frame type.
     */
    private static FrameType getFrameType(short control) throws NoSuchElementException {
        return FrameType.valueOf(control & 0b111).get();
    }

    /**
     * Gets the retransmission bit from a control field.
     * @param control The control field to read from.
     * @return The retransmission bit.
     */
    private static boolean getRetransmission(short control) {
        return (control >>> 3 & 0b1) == 1;
    }

    /**
     * Gets the frame number from a control field.
     * @param control The control field to read from.
     * @return The frame number.
     */
    private static short getFrameNumber(short control) {
        return (short)(control >>> 4);
    }
}
