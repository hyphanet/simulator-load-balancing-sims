// A high-level message (as opposed to a low-level packet)

class Message
{
	public final static int HEADER_SIZE = 30; // Sequence number, MAC, etc
	public final static int ID_SIZE = 16; // Size of unique request ID
	public final static int KEY_SIZE = 32; // Size of routing key
	public final static int DATA_SIZE = 1024; // Size of data block
	
	public int size; // Size in bytes
}
