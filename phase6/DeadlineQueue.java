// A queue storing outgoing messages and their coalescing deadlines

import java.util.LinkedList;
import messages.Message;

class DeadlineQueue
{
	public int size = 0; // Size in bytes
	private LinkedList<Message> messages = new LinkedList<Message>();
	private LinkedList<Double> deadlines = new LinkedList<Double>();
	
	public void add (Message m, double deadline)
	{
		size += m.size;
		messages.add (m);
		deadlines.add (deadline);
	}
	
	public int headSize()
	{
		if (messages.isEmpty()) return 0;
		else return messages.peek().size;
	}
	
	public double deadline()
	{
		Double deadline = deadlines.peek();
		if (deadline == null) return Double.POSITIVE_INFINITY;
		else return deadline;
	}
	
	public Message pop()
	{
		deadlines.poll();
		Message m = messages.poll();
		if (m != null) size -= m.size;
		return m;
	}
}
