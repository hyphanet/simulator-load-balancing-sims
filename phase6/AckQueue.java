// A queue storing outgoing acks and their coalescing deadlines

import java.util.LinkedList;

class AckQueue
{
	public int size = 0; // Size in bytes
	private LinkedList<Integer> acks = new LinkedList<Integer>();
	private LinkedList<Double> deadlines = new LinkedList<Double>();
	
	public void add (int ack, double deadline)
	{
		size += Packet.ACK_SIZE;
		acks.add (ack);
		deadlines.add (deadline);
	}
	
	public double deadline()
	{
		Double deadline = deadlines.peek();
		if (deadline == null) return Double.POSITIVE_INFINITY;
		else return deadline;
	}
	
	public Integer pop()
	{
		deadlines.poll();
		Integer ack = acks.poll();
		if (ack != null) size -= Packet.ACK_SIZE;
		return ack;
	}
}
