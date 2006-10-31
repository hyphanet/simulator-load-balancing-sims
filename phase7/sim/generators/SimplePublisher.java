// A simple publisher that inserts CHKs using a Poisson process and informs
// each reader of each key after an average of one minute

package sim.generators;
import sim.Event;
import sim.EventTarget;
import sim.Node;
import java.util.HashSet;

public class SimplePublisher implements EventTarget
{
	public final double rate; // Inserts per second
	private Node node; // The publisher's node
	private HashSet<Node> readers; // The readers' nodes
	
	public SimplePublisher (double rate, Node node)
	{
		this.rate = rate;
		this.node = node;
		readers = new HashSet<Node>();
		// Schedule the first insert
		double delay = -Math.log (Math.random()) / rate;
		Event.schedule (this, delay, INSERT_CHK, null);
	}
	
	public boolean addReader (Node n)
	{
		return readers.add (n);
	}
	
	// Event callbacks
	
	private void insertChk()
	{
		// Insert a random key
		int key = Node.locationToKey (Math.random());
		Event.schedule (node, 0.0, Node.INSERT_CHK, key);
		// Inform each reader after an average of one minute
		for (Node n : readers) {
			double delay = -Math.log (Math.random()) * 60.0;
			Event.schedule (n, delay, Node.REQUEST_CHK, key);
		}
		// Schedule the next insert
		double delay = -Math.log (Math.random()) / rate;
		Event.schedule (this, delay, INSERT_CHK, null);
	}
	
	// EventTarget interface
	
	public void handleEvent (int type, Object data)
	{
		if (type == INSERT_CHK) insertChk();
	}
	
	private final static int INSERT_CHK = 1;
}
