package wifi;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Optional;
import java.util.NoSuchElementException;
import java.util.zip.CRC32;

/**
 * 802.11~ packet.
 */
public class Packet {
    private static final short BROADCAST_ADDR = (short)0xFFFF;

    private final FrameType type;
    private final boolean retransmission;
    private final short frameNumber;
    private final short destAddr;
    private final short srcAddr;
    private final byte[] data;
    private final int crc;
    private final ByteBuffer frame;

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
     */
    public Packet(FrameType type, boolean retransmission, short frameNumber, short destAddr, short srcAddr, byte[] data) {
        this.type = type;
        this.retransmission = retransmission;
        this.frameNumber = frameNumber;
        this.destAddr = destAddr;
        this.srcAddr = srcAddr;
        this.data = data.clone();

        // Build frame
        short control = Packet.buildControlField(type, retransmission, frameNumber);
        ByteBuffer frame;
        frame = ByteBuffer.allocate(6 + this.data.length + 4);
        frame.putShort(control);
        frame.putShort(this.destAddr);
        frame.putShort(this.srcAddr);
        frame.put(this.data);
        CRC32 crc32 = new CRC32();
        crc32.update(frame.array(), 0, 6 + this.data.length);
        this.crc = (int)crc32.getValue();
        frame.putInt(this.crc);
        this.frame = frame;
    }

    /**
     * Incoming packet constructor.
     * @param frame Raw data of the frame.
     */
    public Packet(byte[] frame) {
        this.frame = ByteBuffer.wrap(frame);

        // Parse frame
        short control = this.frame.getShort();
        this.destAddr = this.frame.getShort();
        this.srcAddr = this.frame.getShort();
        byte[] data = new byte[frame.length - 10];
        this.frame.get(data);
        this.data = data;
        this.crc = this.frame.getInt();

        // Parse control field
        this.type = Packet.extractFrameType(control);
        this.retransmission = Packet.extractRetransmission(control);
        this.frameNumber = Packet.extractFrameNumber(control);
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

    public FrameType getFrameType() {
        return this.type;
    }

    public short getFrameNumber() {
        return this.frameNumber;
    }

    /**
     * Checks whether this packet is a broadcast packet.
     * @return Whether this packet is a broadcast packet.
     */
    public boolean isBroadcast() {
        return this.destAddr == Packet.BROADCAST_ADDR;
    }

    /**
     * @return Whether the checksum for this packet is valid.
     */
    public boolean checksumValid() {
        CRC32 crc32 = new CRC32();
        crc32.update(frame.array(), 0, 6 + this.data.length);
        return this.crc == (int)crc32.getValue();
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
        int rtBit = (retransmission ? 1 : 0) << 12;
        int typeBits = type.value << 13;
        return (short)(frameNumber + rtBit + typeBits);
    }

    /**
     * Gets the frame type from a control field.
     * @param control The control field to read from.
     * @return The frame type.
     */
    private static FrameType extractFrameType(short control) {
        FrameType type;
        try {
            type = FrameType.valueOf((control >>> 13) & 0b111).get();
        } catch (NoSuchElementException e) {
            type = FrameType.DATA;
        }
        return type;
    }

    /**
     * Gets the retransmission bit from a control field.
     * @param control The control field to read from.
     * @return The retransmission bit.
     */
    private static boolean extractRetransmission(short control) {
        return ((control >>> 12) & 0b1) == 1;
    }

    /**
     * Gets the frame number from a control field.
     * @param control The control field to read from.
     * @return The frame number.
     */
    private static short extractFrameNumber(short control) {
        return (short)(control & 0b1111_1111_1111);
    }
}
