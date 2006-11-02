// A simple publisher that inserts CHKs using a Poisson process and informs
// each reader of each key after an average of ten minutes

package sim.generators;
import sim.Event;
import sim.EventTarget;
import sim.Node;
import java.util.HashSet;

public class SimplePublisher implements EventTarget
{
	public final double rate; // Inserts per second
	private int inserts; // Publish this many inserts (0 for unlimited)
	private Node node; // The publisher's node
	private HashSet<Node> readers; // The readers' nodes
	
	public SimplePublisher (double rate, int inserts, Node node)
	{
		this.rate = rate;
		this.inserts = inserts;
		this.node = node;
		readers = new HashSet<Node>();
		// Schedule the first insert
		double delay = -Math.log (Math.random()) / rate;
		Event.schedule (this, delay, PUBLISH, null);
	}
	
	public boolean addReader (Node n)
	{
		return readers.add (n);
	}
	
	// Event callbacks
	
	private void publish()
	{
		// Insert a random key
		int key = Node.locationToKey (Math.random());
		node.generateChkInsert (key);
		// Inform each reader after an average of ten minutes
		for (Node n : readers) {
			double delay = 595.0 + Math.random() * 10.0;
			Event.schedule (n, delay, Node.REQUEST_CHK, key);
		}
		// Schedule the next insert after an exp. distributed delay
		if (inserts > 0 && --inserts == 0) return;
		double delay = -Math.log (Math.random()) / rate;
		Event.schedule (this, delay, PUBLISH, null);
	}
	
	// EventTarget interface
	
	public void handleEvent (int type, Object data)
	{
		if (type == PUBLISH) publish();
	}
	
	private final static int PUBLISH = 1;
}
