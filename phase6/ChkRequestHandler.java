// The state of an outstanding CHK request as stored at each node along the path

import java.util.LinkedList;
import messages.*;

class ChkRequestHandler implements EventTarget
{
	// State machine
	public final static int STARTED = 0;
	public final static int REQUEST_SENT = 1;
	public final static int ACCEPTED = 2;
	public final static int TRANSFERRING = 3;
	public final static int COMPLETED = 4;
	
	private final int id; // The unique ID of the request
	private final int key; // The requested key
	private double best; // The best location seen so far
	private int htl; // Hops to live for backtracking
	
	private Node node; // The owner of this RequestHandler
	private Peer prev; // The previous hop of the request
	private Peer next = null; // The (current) next hop of the request
	private LinkedList<Peer> nexts; // Candidates for the next hop
	private int state = STARTED; // State machine
	private int blockBitmap = 0; // Bitmap of received blocks
	
	public ChkRequestHandler (ChkRequest r, Node node, Peer prev)
	{
		id = r.id;
		key = r.key;
		best = r.best;
		htl = r.htl;
		this.node = node;
		this.prev = prev;
		nexts = new LinkedList<Peer> (node.peers());
		nexts.remove (prev);
		// If this is the best node so far, update best & reset htl
		double target = Node.keyToLocation (key);
		if (Node.distance (target, node.location)
		< Node.distance (target, best)) {
			node.log ("resetting htl of " + this);
			best = node.location;
			htl = ChkRequest.MAX_HTL;
		}
	}
	
	// Remove a peer from the list of candidates for the next hop
	public void removeNextHop (Peer p)
	{
		nexts.remove (p);
	}
	
	public void handleMessage (Message m, Peer src)
	{
		if (src != next) {
			node.log ("unexpected source for " + m);
			return;
		}
		if (m instanceof Accepted) handleAccepted ((Accepted) m);
		else if (m instanceof ChkDataFound)
			handleChkDataFound ((ChkDataFound) m);
		else if (m instanceof DataNotFound)
			handleDataNotFound ((DataNotFound) m);
		else if (m instanceof RouteNotFound)
			handleRouteNotFound ((RouteNotFound) m);
		else if (m instanceof Block) handleBlock ((Block) m);
		else if (m instanceof RouteNotFound) forwardRequest();
		else if (m instanceof RejectedLoop) forwardRequest();
		else node.log ("unexpected type for " + m);
	}
	
	private void handleAccepted (Accepted a)
	{
		if (state != REQUEST_SENT) node.log (a + " out of order");
		if (state != TRANSFERRING) state = ACCEPTED;
		// Wait 60 seconds for the next hop to start sending the data
		Event.schedule (this, 60.0, FETCH_TIMEOUT, next);
	}
	
	private void handleChkDataFound (ChkDataFound df)
	{
		if (state != ACCEPTED) node.log (df + " out of order");
		if (prev != null) prev.sendMessage (df); // Forward the message
		state = TRANSFERRING;
		// Wait 5 minutes for the transfer to complete
		Event.schedule (this, 300.0, TRANSFER_TIMEOUT, next);
	}
	
	private void handleDataNotFound (DataNotFound dnf)
	{
		if (state != ACCEPTED) node.log (dnf + " out of order");
		if (prev == null) node.log (this + " failed");
		else prev.sendMessage (dnf); // Forward the message
		node.chkRequestCompleted (id);
		state = COMPLETED;
	}
	
	private void handleRouteNotFound (RouteNotFound rnf)
	{
		if (state != ACCEPTED) node.log (rnf + " out of order");
		if (rnf.htl < htl) htl = rnf.htl;
		// Use the remaining htl to try another peer
		nexts.remove (next);
		forwardRequest();
	}
	
	private void handleBlock (Block b)
	{
		if (state != TRANSFERRING) node.log (b + " out of order");
		if (receivedBlock (b.index)) return; // Ignore duplicates
		// Forward the block
		if (prev != null) {
			node.log ("forwarding " + b);
			prev.sendMessage (b);
		}
		// If the transfer is complete, cache the data
		if (receivedAll()) {
			node.cacheChk (key);
			if (prev == null) node.log (this + " succeeded");
			node.chkRequestCompleted (id);
			state = COMPLETED;
		}
	}
	
	public void forwardRequest()
	{
		// If the request has run out of hops, send DataNotFound
		if (htl == 0) {
			node.log ("data not found for " + this);
			if (prev == null) node.log (this + " failed");
			else prev.sendMessage (new DataNotFound (id));
			node.chkRequestCompleted (id);
			state = COMPLETED;
			return;
		}
		// Forward the request to the best remaining peer
		next = bestPeer();
		if (next == null) {
			node.log ("route not found for " + this);
			if (prev == null) node.log (this + " failed");
			else prev.sendMessage (new RouteNotFound (id, htl));
			node.chkRequestCompleted (id);
			state = COMPLETED;
			return;
		}
		// Decrement htl if next node is not best so far
		double target = Node.keyToLocation (key);
		if (Node.distance (target, next.location)
		> Node.distance (target, best)) {
			htl = node.decrementHtl (htl);
			node.log (this + " has htl " + htl);
		}
		node.log ("forwarding " + this + " to " + next.address);
		next.sendMessage (new ChkRequest (id, key, best, htl));
		nexts.remove (next);
		state = REQUEST_SENT;
		// Wait 5 seconds for the next hop to accept the request
		Event.schedule (this, 5.0, ACCEPTED_TIMEOUT, next);
	}
	
	// Find the best remaining peer
	private Peer bestPeer ()
	{
		double keyLoc = Node.keyToLocation (key);
		double bestDist = Double.POSITIVE_INFINITY;
		Peer bestPeer = null;
		for (Peer peer : nexts) {
			double dist = Node.distance (keyLoc, peer.location);
			if (dist < bestDist) {
				bestDist = dist;
				bestPeer = peer;
			}
		}
		return bestPeer; // Null if the list was empty
	}
	
	// Mark a block as received, return true if it's a duplicate
	private boolean receivedBlock (int index)
	{
		int mask = 1 << index;
		boolean duplicate = (blockBitmap & mask) != 0;
		blockBitmap |= mask;
		return duplicate;
	}
	
	// Return true if all blocks have been received
	private boolean receivedAll()
	{
		return blockBitmap == 0xFFFFFFFF;
	}
	
	public String toString()
	{
		return new String ("CHK request (" + id + "," + key + ")");
	}
	
	// Event callback
	private void acceptedTimeout (Peer p)
	{
		if (p != next) return; // We've already moved on to another peer
		if (state != REQUEST_SENT) return; // Peer has already answered
		node.log (this + " accepted timeout waiting for " + p);
		forwardRequest(); // Try another peer
	}
	
	// Event callback
	private void fetchTimeout (Peer p)
	{
		if (state != ACCEPTED) return; // Peer has already answered
		node.log (this + " fetch timeout waiting for " + p);
		if (prev == null) node.log (this + " failed");
		node.chkRequestCompleted (id);
		state = COMPLETED;
	}
	
	// Event callback
	private void transferTimeout (Peer p)
	{
		if (state != TRANSFERRING) return; // Transfer has completed
		node.log (this + " transfer timeout waiting for " + p);
		if (prev == null) node.log (this + " failed");
		node.chkRequestCompleted (id);
		state = COMPLETED;
	}
	
	// EventTarget interface
	public void handleEvent (int type, Object data)
	{
		switch (type) {
			case ACCEPTED_TIMEOUT:
			acceptedTimeout ((Peer) data);
			break;
			
			case FETCH_TIMEOUT:
			fetchTimeout ((Peer) data);
			break;
			
			case TRANSFER_TIMEOUT:
			transferTimeout ((Peer) data);
			break;
		}
	}
	
	// Each EventTarget class has its own event codes
	private final static int ACCEPTED_TIMEOUT = 1;
	private final static int FETCH_TIMEOUT = 2;
	private final static int TRANSFER_TIMEOUT = 3;
}
