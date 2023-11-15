package wifi;

import java.io.PrintWriter;
import java.util.NoSuchElementException;

import rf.RF;

/**
 * Use this layer as a starting point for your project code.  See {@link Dot11Interface} for more
 * details on these routines.
 * @author richards
 */
public class LinkLayer implements Dot11Interface {
    // RF layer
    private RF rf;
    // Sender thread
    private Sender sender;
    // Receiver thread
    private Receiver receiver;
    // MAC address
    private short mac;
    // Output stream to write to
    private PrintWriter output;
    // Status of the latest operation
    private Status status;
    // Which debug messages to print
    private DebugLevel debugLevel;
    // Whether to select random slots
    private boolean randomSlotSelection;
    // Beacon interval
    private int beaconInterval;
    // Current frame number
    private short frameNumber;

    // Status code names with their associated values
    private enum Status {
        // Initial value if init was successful
        SUCCESS(1),
        // General error code
        UNSPECIFIED_ERROR(2),
        // Attempt to initialize RF layer failed
        RF_INIT_FAILED(3),
        // Last transmission was acknowledged
        TX_DELIVERED(4),
        // Last transmission was abandoned after unsuccessful delivery attempts
        TX_FAILED(5),
        // Buffer size was negative
        BAD_BUF_SIZE(6),
        // Pointer to a buffer or address was NULL
        BAD_ADDRESS(7),
        // Illegal MAC address was specified
        BAD_MAC_ADDRESS(8),
        // One or more arguments are invalid
        ILLEGAL_ARGUMENT(9),
        // Outgoing transmission rejected due to insufficient buffer space
        INSUFFICIENT_BUFFER_SPACE(10);

        // Integer value of status code
        private final int value;

        private Status(int value) {
            this.value = value;
        }
    }

    // Level of debugging info to print
    private enum DebugLevel {
        // Don't print any debug messages
        NONE,
        // Only print errors
        ERRORS,
        // Print all debug messages
        FULL
    }

    /**
     * Constructor takes a MAC address and the PrintWriter to which our output will
     * be written.
     * @param mac  MAC address
     * @param output  Output stream associated with GUI
     */
    public LinkLayer(short mac, PrintWriter output) {
        this.mac = mac;
        this.output = output;      
        this.status = Status.SUCCESS;
        this.debugLevel = DebugLevel.FULL;
        this.randomSlotSelection = false;
        this.beaconInterval = 0;
        this.frameNumber = 0;

        // Create RF Layer
        try {
            this.rf = new RF(null, null);
        } catch (Exception e) {
            this.status = Status.RF_INIT_FAILED;
        }

        // Create sender
        this.sender = new Sender(this.rf);
        new Thread(this.sender).start();

        // Create receiver
        this.receiver = new Receiver(this.rf, this.mac, this.sender);
        new Thread(this.receiver).start();
    }

    /**
     * Send method takes a destination, a buffer (array) of data, and the number
     * of bytes to send.  See docs for full description.
     */
    public int send(short dest, byte[] data, int len) {
        if (this.debugLevel == DebugLevel.FULL) {
            this.output.println("LinkLayer: Sending " + len + " bytes to " + dest);
        }

        // Handle len parameter
        int dataLen = Math.min(data.length, len);
        byte[] trimmedData = new byte[dataLen];
        System.arraycopy(data, 0, trimmedData, 0, dataLen);

        // Build packet
        // TODO: Make all of these params be correct (frame type, crc)
        Packet packet = new Packet(Packet.FrameType.DATA, false, this.nextFrameNumber(), dest, this.mac, trimmedData, 0xFFFF);

        this.sender.send(packet);

        return dataLen;
    }

    /**
     * Recv method blocks until data arrives, then writes it an address info into
     * the Transmission object.  See docs for full description.
     */
    public int recv(Transmission t) {
        Packet packet;
        try {
            // Receive packet
            packet = this.receiver.recv();

            // Pass data to transmission
            t.setBuf(packet.getData());
            t.setDestAddr(packet.getDestAddr());
            t.setSourceAddr(packet.getSrcAddr());

            // Return packet data size
            return packet.getData().length;
        } catch (NoSuchElementException e) {
            if (this.debugLevel == DebugLevel.ERRORS || this.debugLevel == DebugLevel.FULL) {
                this.output.println("Malformed packet received");
            }

            return -1;
        }
    }

    /**
     * Returns a current status code.  See docs for full description.
     */
    public int status() {
        return this.status.value;
    }

    /**
     * Passes command info to your link layer.  See docs for full description.
     */
    public int command(int cmd, int val) {
        switch (cmd) {
        // Summarize command options
        case 0:
            this.output.printf("Command 0: Summarize all command options and report their current settings\n"
                + "Command 1: Debug options\n"
                    + "\tValue 0: Disable debugging\n"
                    + "\tValue 1: Enable only error messages\n"
                    + "\tValue 2: Enable all debugging\n"
                + "Command 2: Slot selection\n"
                    + "\tValue 0: Slots are selected randomly\n"
                    + "\tFor any other value, maxCW is selected\n"
                + "Command 3: Beacon interval\n"
                    + "\tValue -1: Sending beacon frames is disabled\n"
                    + "\tAny other (nonnegative) value specifies the number of seconds between the start of beacon transmissions\n");
            break;

        // Debug options
        case 1:
            switch (val) {
            case 0:
                this.debugLevel = DebugLevel.NONE;
                break;
            case 1:
                this.debugLevel = DebugLevel.ERRORS;
            case 2:
                this.debugLevel = DebugLevel.FULL;
                break;
            default:
                this.status = Status.ILLEGAL_ARGUMENT;
            }
            break;

        // Slot selection
        case 2:
            if (val == 0) {
                this.randomSlotSelection = true;
            } else {
                this.randomSlotSelection = false;
            }
            break;
        
        // Beacon interval
        case 3:
            if (val < -1) {
                this.status = Status.ILLEGAL_ARGUMENT;
            } else {
                this.beaconInterval = val;
            }
            break;
        default:
            this.status = Status.ILLEGAL_ARGUMENT;
        }

        return 0;
    }

    private short nextFrameNumber() {
        short frameNumber = this.frameNumber;
        ++this.frameNumber;
        return frameNumber;
    }
}
