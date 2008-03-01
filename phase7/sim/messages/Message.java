// This software has been placed in the public domain by its author

// A high-level message (as opposed to a low-level packet)

package sim.messages;

public class Message
{
	public final static int HEADER_SIZE = 12; // Bytes, including search ID
	public final static int KEY_SIZE = 32; // Size of a routing key, bytes
	public final static int PUB_KEY_SIZE = 1024; // Size of a pub key, bytes
	public final static int DATA_SIZE = 1024; // Size of a data block, bytes
	public final static int ACK_SIZE = 4; // Size of a sequence num, bytes
	
	public static int nextId = 0; // Each search has a unique ID
	
	public int id; // Search ID
	public double deadline = 0.0; // Coalescing deadline
	
	// Override this
	public int size()
	{
		return HEADER_SIZE;
	}
}
