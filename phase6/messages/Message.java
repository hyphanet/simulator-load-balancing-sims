// A high-level message (as opposed to a low-level packet)

package messages;

public class Message
{
	public final static int HEADER_SIZE = 4; // Message type etc
	public final static int ID_SIZE = 8; // Size of unique request ID
	public final static int KEY_SIZE = 32; // Size of routing key
	
	public int size; // Size in bytes
}
