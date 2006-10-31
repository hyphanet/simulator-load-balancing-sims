package messages;

public class Ack extends Message
{
	public Ack (int seq, double deadline)
	{
		id = seq; // Space-saving hack
		this.deadline = deadline;
	}
	
	public int size()
	{
		return ACK_SIZE;
	}
}
