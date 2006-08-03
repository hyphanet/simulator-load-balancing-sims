// A low-level packet (as opposed to a high-level message)

import java.util.ArrayList;

abstract class Packet
{
	public final static int HEADER_SIZE = 80; // Including IP & UDP headers
	public final static int MAX_PAYLOAD = 1400;
	public final static int SENSIBLE_PAYLOAD = 1000; // Nagle's algorithm
	
	public int src, dest; // Network addresses
	public int size; // Packet size in bytes, including headers
	public double latency; // Link latency (stored here for convenience)
}

class DataPacket extends Packet
{
	public int seq; // Sequence number
	public ArrayList<Message> messages = null; // Payload	
	public double sent; // Time at which the packet was (re)transmitted
	
	public DataPacket (int dataSize)
	{
		size = dataSize + HEADER_SIZE;
	}
	
	// In real life the payload would be an array of bytes
	public void addMessage (Message m)
	{
		if (messages == null) messages = new ArrayList<Message>();
		messages.add (m);
	}
}

class Ack extends Packet
{
	public int ack; // Explicit ack of a DataPacket's sequence number
	
	public Ack (int ack)
	{
		size = HEADER_SIZE;
		this.ack = ack;
	}
}
