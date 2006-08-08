// Tell the sender how long each ack was delayed so it can measure the RTT

class Ack
{
	public final int seq; // Sequence number of an acked packet
	public final double delay; // Seconds the ack was delayed for coalescing
	
	public Ack (int seq, double delay)
	{
		this.seq = seq;
		this.delay = delay;
	}
}
