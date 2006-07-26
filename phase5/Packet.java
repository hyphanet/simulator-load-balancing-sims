// A low-level packet (as opposed to a high-level message)

import java.util.ArrayList;

abstract class Packet
{
	public final static int HEADER_SIZE = 50;
	public final static int MAX_PAYLOAD = 1400;
	
	public int src, dest; // Network addresses
	public int size; // Packet size in bytes, including headers
	public int seq; // Sequence number or explicit ack
	public double latency; // Link latency (stored here for convenience)
}

class DataPacket extends Packet
{
	public ArrayList<Message> messages = null; // Payload	
	public double sent; // Time at which the packet was (re)transmitted
	
	public DataPacket (int dataSize)
	{
		size = dataSize + HEADER_SIZE;
	}
	
	/*
	In real life the payload would be an array of bytes, but here the 
	payload is represented by an ArrayList of Messages. A large message can
	be split across more than one packet, in which case the message only
	appears in the payload of the *last* packet. This means it's possible
	for a full packet to have an apparently empty payload.
	*/
	
	public void addMessage (Message m)
	{
		if (messages == null) messages = new ArrayList<Message>();
		messages.add (m);
	}
}

class Ack extends Packet
{
	public Ack (int seq)
	{
		size = HEADER_SIZE;
		this.seq = seq;
	}
}
