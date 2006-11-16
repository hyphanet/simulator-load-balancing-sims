// The state of a search as stored at each node along the path

package sim.handlers;
import sim.*;
import sim.messages.*;
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
	
	public final Node node; // The owner of this MessageHandler
	public final Peer prev; // The previous hop of the search
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
	
	// Forward the search to the closest remaining peer, if any
	public void forwardSearch()
	{
		next = null;
		// If the search has run out of hops, reply and finish
		if (htl == 0) {
			node.log (this + " has no hops remaining");
			sendReply();
			finish();
			return;
		}
		// Find the closest remaining peer
		next = closestPeer();
		if (next == null) {
			node.log ("route not found for " + this);
			if (prev == null) node.log (this + " failed");
			else prev.sendMessage (new RouteNotFound (id, htl));
			finish();
			return;
		}
		// Decrement the htl if the next node is not the closest so far
		double target = Node.keyToLocation (key);
		if (Node.distance (target, next.location)
		>= Node.distance (target, closest))
			htl = node.decrementHtl (htl);
		node.log (this + " has htl " + htl);
		// Consume a token
		next.tokensOut--;
		// Forward the search
		node.log ("forwarding " + this + " to " + next.address);
		next.sendMessage (makeSearchMessage());
		nexts.remove (next);
		searchState = SENT;
		// Wait for the next hop to accept the search
		scheduleAcceptedTimeout (next);
	}
	
	// Find the closest remaining peer, if any
	private Peer closestPeer ()
	{
		double now = Event.time();
		double keyLoc = Node.keyToLocation (key);
		double closestDist = Double.POSITIVE_INFINITY;
		Peer closestPeer = null;
		for (Peer peer : nexts) {
			if (peer.tokensOut == 0) {
				node.log ("bypassing busy peer " + peer);
				continue;
			}
			if (now < peer.backoffUntil) {
				node.log ("bypassing backed off peer " + peer);
				continue;
			}
			double dist = Node.distance (keyLoc, peer.location);
			if (dist < closestDist) {
				closestDist = dist;
				closestPeer = peer;
			}
		}
		return closestPeer; // Null if there are no suitable peers
	}
	
	protected void handleRejectedLoop (RejectedLoop rl)
	{
		if (searchState != SENT) node.log (rl + " out of order");
		next.successNotOverload(); // Reset the backoff length
		next.tokensOut++; // No token was consumed
		forwardSearch();
	}
	
	protected void handleRejectedOverload (RejectedOverload ro)
	{
		if (searchState != SENT) node.log (ro + " out of order");
		if (ro.local) {
			ro.local = false;
			// Back off and try another peer
			next.localRejectedOverload();
			forwardSearch();
		}
		if (prev == null) {
			// FIXME: throttle
		}
		else prev.sendMessage (ro); // Forward the message
	}
	
	protected void handleRouteNotFound (RouteNotFound rnf)
	{
		if (searchState != ACCEPTED) node.log (rnf + " out of order");
		next.successNotOverload(); // Reset the backoff length
		// Use the remaining htl to try another peer
		if (rnf.htl < htl) htl = rnf.htl;
		forwardSearch();
	}
	
	// Event callback
	protected void acceptedTimeout (Peer p)
	{
		if (p != next) return; // We've already moved on to another peer
		if (searchState != SENT) return;
		node.log (this + " accepted timeout for " + p);
		p.localRejectedOverload(); // Back off from p
		// Tell the sender to slow down
		if (prev == null) {
			// FIXME: throttle
		}
		else prev.sendMessage (new RejectedOverload (id, false));
		// Try another peer
		forwardSearch();
	}
	
	// Event callback
	protected void searchTimeout (Peer p)
	{
		if (p != next) return; // We've already moved on to another peer
		if (searchState != ACCEPTED) return;
		node.log (this + " search timeout for " + p);
		p.localRejectedOverload(); // Back off from p
		// Tell the sender to slow down
		if (prev == null) {
			// FIXME: throttle
		}
		else prev.sendMessage (new RejectedOverload (id, false));
		if (prev == null) node.log (this + " failed");
		finish();
	}
	
	public abstract void handleMessage (Message m, Peer src);
	protected abstract void sendReply();
	protected abstract Search makeSearchMessage();
	protected abstract void scheduleAcceptedTimeout (Peer next);
	protected abstract void finish();
}
