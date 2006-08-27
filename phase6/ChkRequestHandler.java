// The state of an outstanding CHK request, stored at each node along the path

import java.util.LinkedList;
import messages.*;

class ChkRequestHandler
{
	// State machine
	public final static int REQUEST_SENT = 1;
	public final static int ACCEPTED = 2;
	public final static int TRANSFERRING = 3;
	
	public final int id; // The unique ID of the request
	public final int key; // The requested key
	private Node node; // The owner of this RequestHandler
	private Peer prev; // The previous hop of the request
	private Peer next = null; // The (current) next hop of the request
	private LinkedList<Peer> nexts; // Candidates for the next hop
	private int state = REQUEST_SENT; // State machine
	private int blockBitmap = 0; // Bitmap of received blocks
	
	public ChkRequestHandler (ChkRequest r, Node node, Peer prev)
	{
		id = r.id;
		key = r.key;
		this.node = node;
		this.prev = prev;
		nexts = new LinkedList<Peer> (node.peers());
	}
	
	// Remove a peer from the list of candidates for the next hop
	public void removeNextHop (Peer p)
	{
		nexts.remove (p);
	}
	
	public boolean handleMessage (Message m, Peer src)
	{
		if (src != next) {
			node.log ("unexpected source for " + m);
			return false; // Request not completed
		}
		if (m instanceof Accepted) return handleAccepted ((Accepted) m);
		if (m instanceof ChkDataFound)
			return handleChkDataFound ((ChkDataFound) m);
		if (m instanceof Block) return handleBlock ((Block) m);
		if (m instanceof RouteNotFound) return forwardRequest();
		if (m instanceof RejectedLoop) return forwardRequest();
		// Unrecognised message type
		node.log ("unexpected type for " + m);
		return false; // Request not completed
	}
	
	private boolean handleAccepted (Accepted a)
	{
		if (state != REQUEST_SENT)
			node.log (a + " received out of order");
		state = ACCEPTED;
		return false; // Request not completed
	}
	
	private boolean handleChkDataFound (ChkDataFound df)
	{
		if (state != ACCEPTED)
			node.log (df + " received out of order");
		state = TRANSFERRING;
		if (prev != null) prev.sendMessage (df);
		return false; // Request not completed
	}
	
	private boolean handleBlock (Block b)
	{
		if (state != TRANSFERRING)
			node.log (b + " received out of order");
		if (receivedBlock (b.index)) return false; // Ignore duplicates
		// Forward the block
		if (prev != null) {
			node.log ("forwarding " + b);
			prev.sendMessage (b);
		}
		if (receivedAll()) {
			node.storeChk (key);
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
			next.sendMessage (new ChkRequest (id, key));
			nexts.remove (next);
			return false; // Request not completed
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
}
