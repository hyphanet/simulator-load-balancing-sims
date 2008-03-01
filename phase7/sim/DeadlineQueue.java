// This software has been placed in the public domain by its author

// A queue storing outgoing messages and their coalescing deadlines

package sim;
import sim.messages.Message;
import java.util.LinkedList;

class DeadlineQueue<MESSAGE extends Message>
{
	public int size = 0; // Size of the queue in bytes
	private LinkedList<MESSAGE> messages = new LinkedList<MESSAGE>();
	
	public void add (MESSAGE m)
	{
		size += m.size();
		messages.add (m);
	}
	
	public int headSize()
	{
		if (messages.isEmpty()) return 0;
		else return messages.peek().size();
	}
	
	public double deadline()
	{
		if (messages.isEmpty()) return Double.POSITIVE_INFINITY;
		else return messages.peek().deadline;
	}
	
	public MESSAGE pop()
	{
		MESSAGE m = messages.poll();
		if (m != null) size -= m.size();
		return m;
	}
}
