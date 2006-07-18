// A high-level message (as opposed to a low-level packet)

class Message
{
	public int seq; // Sequence number
	public int size; // Size in bytes
	
	public Message (int seq, int size)
	{
		this.seq = seq;
		this.size = size;
	}
}
