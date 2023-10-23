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
        this.debugLevel = DebugLevel.NONE;
        this.randomSlotSelection = false;

        // Create RF Layer
        try {
            this.rf = new RF(null, null);
        } catch (Exception e) {
            this.status = Status.RF_INIT_FAILED;
        }
    }

    /**
     * Send method takes a destination, a buffer (array) of data, and the number
     * of bytes to send.  See docs for full description.
     */
    public int send(short dest, byte[] data, int len) {
        output.println("LinkLayer: Sending "+len+" bytes to "+dest);
        this.rf.transmit(data);

        return len;
    }

    /**
     * Recv method blocks until data arrives, then writes it an address info into
     * the Transmission object.  See docs for full description.
     */
    public int recv(Transmission t) {
        output.println("LinkLayer: Pretending to block on recv()");
        while (true) {
            Packet packet;
            try {
                packet = new Packet(this.rf.receive());
                output.println(packet);
            } catch (NoSuchElementException e) {
                output.println("Malformed packet received");
            }
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
                    + "\tValue 1: Enable all debugging\n"
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
}
