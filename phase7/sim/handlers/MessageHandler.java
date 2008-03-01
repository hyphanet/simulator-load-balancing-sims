// This software has been placed in the public domain by its author

// The state of a search as stored at each node along the path

package sim.handlers;
import sim.*;
import sim.messages.*;
import java.util.LinkedList;

public abstract class MessageHandler
{
	public final static boolean LOG = false;
	
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
			if (LOG) node.log ("resetting htl of " + this);
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
			if (LOG) node.log (this + " has no hops remaining");
			sendReply();
			finish();
			return;
		}
		// Find the closest remaining peer
		next = closestPeer();
		if (next == null) {
			if (LOG) node.log ("route not found for " + this);
			if (prev == null) {
				if (LOG) node.log (this + " failed (rnf)");
				Node.failed++;
				node.increaseSearchRate(); // Yes, increase
			}
			else prev.sendMessage (new RouteNotFound (id, htl));
			finish();
			return;
		}
		// Decrement the htl if the next node is not the closest so far
		double target = Node.keyToLocation (key);
		if (Node.distance (target, next.location)
		>= Node.distance (target, closest))
			htl = node.decrementHtl (htl);
		if (LOG) node.log (this + " has htl " + htl);
		// Consume a token
		if (Node.useTokens) next.removeTokensOut (1);
		// Forward the search
		if (LOG) node.log ("forwarding " +this+ " to " + next.address);
		next.sendMessage (makeSearchMessage());
		nexts.remove (next);
		searchState = SENT;
		// Wait for the next hop to accept the search
		scheduleAcceptedTimeout (next);
	}
	
	// Find the closest remaining peer, if any
	private Peer closestPeer()
	{
		Peer p = closestPeer (Node.useBackoff);
		// If all peers are backed off, try again ignoring backoff
		if (p == null && Node.useBackoff) {
			if (LOG) node.log ("considering backed off peers");
			return closestPeer (false);
		}
		else return p;
	}
	
	private Peer closestPeer (boolean useBackoff)
	{
		double now = Event.time();
		double keyLoc = Node.keyToLocation (key);
		double closestDist = Double.POSITIVE_INFINITY;
		Peer closestPeer = null;
		for (Peer peer : nexts) {
			if (Node.useTokens && peer.getTokensOut() == 0) {
				if (LOG) node.log ("no tokens for " + peer);
				continue;
			}
			if (useBackoff && now < peer.backoffUntil) {
				if (LOG) node.log ("backed off from " + peer
					+ " until " + peer.backoffUntil);
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
		if (searchState != SENT && LOG) node.log (rl + " out of order");
		next.successNotOverload(); // Reset the backoff length
		forwardSearch();
	}
	
	protected void handleRejectedOverload (RejectedOverload ro)
	{
		if (ro.local) {
			ro.local = false;
			next.localRejectedOverload(); // Back off
			forwardSearch(); // Try another peer
		}
		// Tell the sender to slow down
		if (prev == null) node.decreaseSearchRate();
		else prev.sendMessage (ro); // Forward the message
	}
	
	protected void handleRouteNotFound (RouteNotFound rnf)
	{
		if (searchState != ACCEPTED && LOG)
			node.log (rnf + " out of order");
		// Use the remaining htl to try another peer
		if (rnf.htl < htl) htl = rnf.htl;
		forwardSearch();
	}
	
	// Event callback
	protected void acceptedTimeout (Peer p)
	{
		if (p != next) return; // We've already moved on to another peer
		if (searchState != SENT) return;
		if (LOG) node.log (this + " accepted timeout for " + p);
		p.localRejectedOverload(); // Back off from p
		// Tell the sender to slow down
		if (prev == null) node.decreaseSearchRate();
		else prev.sendMessage (new RejectedOverload (id, false));
		// Try another peer
		forwardSearch();
	}
	
	// Event callback
	protected void searchTimeout (Peer p)
	{
		if (p != next) return; // We've already moved on to another peer
		if (searchState != ACCEPTED) return;
		if (LOG) node.log (this + " search timeout for " + p);
		p.localRejectedOverload(); // Back off from p
		// Tell the sender to slow down
		if (prev == null) {
			if (LOG) node.log (this + " failed (search)");
			Node.failed++;
			node.decreaseSearchRate();
		}
		else prev.sendMessage (new RejectedOverload (id, false));
		finish();
	}
	
	public abstract void handleMessage (Message m, Peer src);
	protected abstract void sendReply();
	protected abstract Search makeSearchMessage();
	protected abstract void scheduleAcceptedTimeout (Peer next);
	protected abstract void finish();
}
