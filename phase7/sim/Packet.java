// A low-level packet (as opposed to a high-level message)

package sim;
import sim.messages.Ack;
import sim.messages.Message;
import java.util.ArrayList;

class Packet
{
	public final static int HEADER_SIZE = 70; // Including IP & UDP headers
	public final static int ACK_SIZE = 4; // Size of an ack in bytes
	public final static int MAX_SIZE = 1450; // MTU including headers
	public final static int SENSIBLE_PAYLOAD = 1000; // Coalescing
	
	public final int src, dest; // Network addresses
	public int size = HEADER_SIZE; // Size in bytes, including headers
	public int seq = -1; // Data sequence number (-1 if no data)
	public ArrayList<Ack> acks = null;
	public ArrayList<Message> messages = null;
	
	public double sent; // Time at which the packet was (re) transmitted
	public double latency; // Link latency, stored here for convenience
	
	public Packet (int src, int dest, double latency)
	{
		this.src = src;
		this.dest = dest;
		this.latency = latency;
	}
	
	public void addAck (Ack a)
	{
		if (acks == null) acks = new ArrayList<Ack>();
		acks.add (a);
		size += a.size();
	}
	
	public void addMessage (Message m)
	{
		if (messages == null) messages = new ArrayList<Message>();
		messages.add (m);
		size += m.size();
	}
	
	public void addMessages (DeadlineQueue q, int maxSize)
	{
		while (q.size > 0 && size + q.headSize() <= maxSize)
			addMessage (q.pop());
	}
	
	public String toString()
	{
		return new String ("packet " + src + ":" + dest + ":" + seq);
	}
}
