package messages;

public class ChkRequest extends Message
{
	private static int nextId = 0;
	
	public final int key; // The requested key
	
	// Start a new request
	public ChkRequest (int key)
	{
		id = nextId++;
		this.key = key;
		size = Message.HEADER_SIZE + Message.KEY_SIZE;
	}
	
	// Forward a request
	public ChkRequest (int id, int key)
	{
		this.id = id;
		this.key = key;
		size = Message.HEADER_SIZE + Message.KEY_SIZE;
	}
	
	public String toString()
	{
		return new String ("CHK request (" + id + "," + key + ")");
	}
}
