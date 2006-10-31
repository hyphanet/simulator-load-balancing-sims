// The state of a search as stored at each node along the path

package sim.handlers;
import sim.Node;
import sim.Peer;
import sim.messages.Search;
import sim.messages.Message;
import java.util.LinkedList;

public abstract class MessageHandler
{
	// State machine
	protected final static int STARTED = 0;
	protected final static int SENT = 1;
	protected final static int ACCEPTED = 2;
	protected final static int TRANSFERRING = 3;
	protected final static int COMPLETED = 4;
	
	protected final int id; // The unique ID of the request or insert
	protected final int key; // The target of the search
	protected double closest; // The closest location seen so far
	protected int htl; // Hops to live for backtracking
	
	protected Node node; // The owner of this MessageHandler
	protected Peer prev; // The previous hop of the search
	protected Peer next = null; // The (current) next hop of the search
	protected LinkedList<Peer> nexts; // Candidates for the next hop
	protected int searchState = STARTED; // The state of the search
	
	public MessageHandler (Search s, Node node, Peer prev)
	{
		id = s.id;
		key = s.key;
		closest = s.closest;
		htl = s.htl;
		this.node = node;
		this.prev = prev;
		nexts = new LinkedList<Peer> (node.peers());
		nexts.remove (prev);
		// If this is the closest location seen so far, reset htl
		double target = Node.keyToLocation (key);
		if (Node.distance (target, node.location)
		< Node.distance (target, closest)) {
			node.log ("resetting htl of " + this); // FIXME
			closest = node.location;
			htl = Search.MAX_HTL;
		}
	}
	
	// Remove a peer from the list of candidates for the next hop
	public void removeNextHop (Peer p)
	{
		nexts.remove (p);
	}
	
	// Find the closest remaining peer
	protected Peer closestPeer ()
	{
		double keyLoc = Node.keyToLocation (key);
		double closestDist = Double.POSITIVE_INFINITY;
		Peer closestPeer = null;
		for (Peer peer : nexts) {
			double dist = Node.distance (keyLoc, peer.location);
			if (dist < closestDist) {
				closestDist = dist;
				closestPeer = peer;
			}
		}
		return closestPeer; // Null if the list was empty
	}
	
	public abstract void handleMessage (Message m, Peer src);
	
	protected abstract Search makeSearchMessage();
}
