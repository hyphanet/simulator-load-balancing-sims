// The state of an outstanding request, stored at each node along the path

import java.util.HashSet;
import java.util.Collection;
import messages.*;

class RequestState
{
	// State machine
	public final static int REQUEST_SENT = 1;
	public final static int TRANSFERRING = 2;
	
	public final int id; // The unique ID of the request
	public final int key; // The requested key
	private Node node; // The owner of this RequestState
	private Peer prev; // The previous hop of the request
	private Peer next; // The (current) next hop of the request
	private HashSet<Peer> nexts; // Possible next hops
	private int state = REQUEST_SENT; // State machine
	private int blockBitmap = 0; // Bitmap of received blocks
	
	public RequestState (Request r, Node node, Peer prev,
				Collection<Peer> peers)
	{
		id = r.id;
		key = r.key;
		this.node = node;
		this.prev = prev;
		next = null;
		nexts = new HashSet<Peer> (peers);
		nexts.remove (prev);
	}
	
	public boolean handleMessage (Message m, Peer src)
	{
		if (src != next) {
			node.log ("unexpected source for " + m);
			return false; // Request not completed
		}
		if (m instanceof Response) return handleResponse ((Response) m);
		else if (m instanceof RouteNotFound) return forwardRequest();
		// Unrecognised message type
		node.log ("unrecognised " + m);
		return false; // Request not completed
	}
	
	private boolean handleResponse (Response r)
	{
		state = TRANSFERRING;
		if (receivedBlock (r.index)) return false; // Ignore duplicates
		// Forward the block
		if (prev != null) {
			node.log ("forwarding " + r);
			prev.sendMessage (r);
		}
		if (receivedAll()) {
			node.cache.put (key);
			if (prev == null) node.log (this + " succeeded");
			return true; // Request completed
		}
		else return false; // Request not completed
	}
	
	public boolean forwardRequest()
	{
		next = closestPeer();
		if (next == null) {
			node.log ("route not found for " + this);
			if (prev == null) node.log (this + " failed");
			else prev.sendMessage (new RouteNotFound (id));
			return true; // Request completed
		}
		else {
			node.log ("forwarding " + this + " to " + next.address);
			next.sendMessage (new Request (id, key));
			nexts.remove (next);
			return false; // Request not completed
		}
	}
	
	// Find the closest peer to the requested key
	private Peer closestPeer()
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
		boolean duplicate = (blockBitmap & 1 << index) != 0;
		blockBitmap |= 1 << index;
		return duplicate;
	}
	
	// Return true if all blocks have been received
	private boolean receivedAll()
	{
		return blockBitmap == 0xFFFFFFFF;
	}
	
	public String toString()
	{
		return new String ("request (" + id + "," + key + ")");
	}
}
