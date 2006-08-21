// A queue storing outgoing messages (including acks and transfers) and their
// coalescing deadlines

import java.util.LinkedList;
import messages.Message;

class DeadlineQueue<T extends Message>
{
	public int size = 0; // Size in bytes
	private LinkedList<T> messages = new LinkedList<T>();
	private LinkedList<Double> deadlines = new LinkedList<Double>();
	
	public void add (T message, double deadline)
	{
		size += message.size;
		messages.add (message);
		deadlines.add (deadline);
	}
	
	public int headSize()
	{
		if (messages.isEmpty()) return 0;
		else return messages.peek().size;
	}
	
	public double deadline()
	{
		Double d = deadlines.peek();
		if (d == null) return Double.POSITIVE_INFINITY;
		else return d;
	}
	
	public T pop()
	{
		deadlines.poll();
		T message = messages.poll();
		size -= message.size;
		return message;
	}
}
