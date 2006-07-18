// A low-level packet containing one or more complete or incomplete messages

// In real life the payload would be an array of bytes, but in the sim the
// payload is represented by an ArrayList of Messages. Large messages can be
// split across more than one packet, in which case the message only appears
// in the payload of the *last* packet. This means it's possible for a full
// packet to have an apparently empty payload.

import java.util.ArrayList;

abstract class Packet
{
	public final static int HEADER_SIZE = 50;
	public final static int MAX_PAYLOAD = 1400;
	
	public int src, dest; // Network addresses
	public int type; // Data, ack, etc
	public int size; // Packet size in bytes, including headers
	public int seq; // Sequence number or explicit ack
	public double latency; // Link latency (stored here for convenience)
}

class DataPacket extends Packet
{
	public ArrayList messages; // Payload	
	public double sent; // Time at which the packet was (re)transmitted
	
	public DataPacket (int dataSize)
	{
		size = dataSize + HEADER_SIZE;
		messages = new ArrayList();
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
