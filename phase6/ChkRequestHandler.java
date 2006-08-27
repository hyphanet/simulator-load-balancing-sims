// The state of an outstanding CHK request, stored at each node along the path

import java.util.LinkedList;
import messages.*;

class ChkRequestHandler implements EventTarget
{
	// State machine
	public final static int STARTED = 0;
	public final static int REQUEST_SENT = 1;
	public final static int ACCEPTED = 2;
	public final static int TRANSFERRING = 3;
	public final static int FAILED = 4;
	
	public final int id; // The unique ID of the request
	public final int key; // The requested key
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
		this.node = node;
		this.prev = prev;
		nexts = new LinkedList<Peer> (node.peers());
		nexts.remove (prev);
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
		else if (m instanceof Block) handleBlock ((Block) m);
		else if (m instanceof RouteNotFound) forwardRequest();
		else if (m instanceof RejectedLoop) forwardRequest();
		else node.log ("unexpected type for " + m);
	}
	
	private void handleAccepted (Accepted a)
	{
		if (state != REQUEST_SENT) node.log (a + " out of order");
		state = ACCEPTED;
		// Wait 60 seconds for the next hop to start sending the data
		Event.schedule (this, 60.0, FETCH_TIMEOUT, next);
	}
	
	private void handleChkDataFound (ChkDataFound df)
	{
		if (state != ACCEPTED) node.log (df + " out of order");
		state = TRANSFERRING;
		if (prev != null) prev.sendMessage (df); // Forward the message
	}
	
	private void handleDataNotFound (DataNotFound dnf)
	{
		if (state != ACCEPTED) node.log (dnf + " out of order");
		if (prev == null) node.log (this + " failed");
		else prev.sendMessage (dnf); // Forward the message
		node.chkRequestCompleted (id);
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
		// If the transfer is complete, store the data
		if (receivedAll()) {
			node.storeChk (key);
			if (prev == null) node.log (this + " succeeded");
			node.chkRequestCompleted (id);
		}
	}
	
	public void forwardRequest()
	{
		next = closestPeer();
		if (next == null) {
			node.log ("route not found for " + this);
			if (prev == null) node.log (this + " failed");
			else prev.sendMessage (new RouteNotFound (id));
			node.chkRequestCompleted (id);
			state = FAILED;
		}
		else {
			node.log ("forwarding " + this + " to " + next.address);
			next.sendMessage (new ChkRequest (id, key));
			nexts.remove (next);
			state = REQUEST_SENT;
			// Wait 5 seconds for the next hop to accept the request
			Event.schedule (this, 5.0, ACCEPTED_TIMEOUT, next);
		}
	}
	
	// Find the closest peer to the requested key
	private Peer closestPeer ()
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
		return new String ("CHK request (" + id + "," + key + ")");
	}
	
	// Event callback
	private void acceptedTimeout (Peer p)
	{
		if (p != next) return; // We've already moved on to another peer
		if (state != REQUEST_SENT) return; // Peer has already answered
		node.log (this + " search timed out waiting for " + p);
		forwardRequest(); // Try another peer
	}
	
	// Event callback
	private void fetchTimeout (Peer p)
	{
		if (state != ACCEPTED) return; // Peer has already answered
		node.log (this + " transfer timed out waiting for " + p);
		if (prev == null) node.log (this + " failed");
		else prev.sendMessage (new DataNotFound (id));
		node.chkRequestCompleted (id);
	}
	
	// EventTarget interface
	public void handleEvent (int type, Object data)
	{
		if (type == ACCEPTED_TIMEOUT) acceptedTimeout ((Peer) data);
		else if (type == FETCH_TIMEOUT) fetchTimeout ((Peer) data);
	}
	
	// Each EventTarget class has its own event codes
	private final static int ACCEPTED_TIMEOUT = 1;
	private final static int FETCH_TIMEOUT = 2;
}
