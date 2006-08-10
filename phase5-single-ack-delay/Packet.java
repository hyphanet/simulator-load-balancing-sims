// A low-level packet (as opposed to a high-level message)

import java.util.ArrayList;

class Packet
{
	public final static int HEADER_SIZE = 82; // Including IP & UDP headers
	public final static int ACK_SIZE = 4; // Size of an ack in bytes
	public final static int MAX_SIZE = 1450; // MTU including headers
	public final static int SENSIBLE_PAYLOAD = 1000; // Coalescing
	
	public int src, dest; // Network addresses
	public int size = HEADER_SIZE; // Size in bytes, including headers
	public int seq = -1; // Data sequence number (-1 for pure acks)
	public double ackDelay; // How many seconds the first ack was delayed
	public ArrayList<Integer> acks = null;
	public ArrayList<Message> messages = null;
	
	public double sent; // Time at which the packet was (re) transmitted
	public double latency; // Link latency (stored here for convenience)
	
	public void addAck (int ack, double delay)
	{
		if (acks == null) {
			acks = new ArrayList<Integer>();
			ackDelay = delay;
		}
		acks.add (ack);
		size += ACK_SIZE;
	}
	
	// In real life the payload would be an array of bytes
	public void addMessage (Message m)
	{
		if (messages == null) messages = new ArrayList<Message>();
		messages.add (m);
		size += m.size;
	}
}
