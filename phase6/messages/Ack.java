package messages;

public class Ack extends Message
{
	public Ack (int seq)
	{
		id = seq; // Space-saving hack
	}
	
	public int size()
	{
		return ACK_SIZE;
	}
}
