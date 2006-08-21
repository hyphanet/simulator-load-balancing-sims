// Note: acks are not FNP messages, they're only here because it makes the
// implementation simpler

package messages;

public class Ack extends Message
{
	public final static int SIZE = 4; // Bytes
	
	public final int seq; // Sequence number of the acknowledged packet
	
	public Ack (int seq)
	{
		this.seq = seq;
		size = SIZE;
	}
}
