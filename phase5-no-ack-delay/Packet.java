// A low-level packet (as opposed to a high-level message)

import java.util.ArrayList;

class Packet
{
	public final static int HEADER_SIZE = 84; // Including IP & UDP headers
	public final static int MAX_SIZE = 1450; // MTU including headers
	public final static int SENSIBLE_PAYLOAD = 1000; // Coalescing
	public final static int NO_DATA = -1; // Packet contains no data
	public final static int NO_ACK = -1; // Packet contains no ack
	
	public int src, dest; // Network addresses
	public int size = HEADER_SIZE; // Size in bytes, including headers
	public int seq = NO_DATA; // Sequence number of this packet
	public int ack = NO_ACK; // Sequence number of an acknowledged packet
	public ArrayList<Message> messages = null;
	
	public double sent; // Time at which the packet was (re) transmitted
	public double latency; // Link latency (stored here for convenience)
	
	public Packet (int ack)
	{
		this.ack = ack;
	}
	
	// In real life the payload would be an array of bytes
	public void addMessage (Message m)
	{
		if (messages == null) messages = new ArrayList<Message>();
		messages.add (m);
		size += m.size;
	}
}
