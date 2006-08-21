// The state of an outstanding request, stored at each node along the path

import java.util.HashSet;
import java.util.Collection;
import messages.Request;

class RequestState
{
	// State machine
	public final static int REQUEST_SENT = 1;
	public final static int TRANSFERRING = 2;
	
	public final int id; // The unique ID of the request
	public final int key; // The requested key
	public final Peer prev; // The previous hop of the request
	public final HashSet<Peer> nexts; // Possible next hops
	public int state = REQUEST_SENT; // State machine
	private int blockBitmap = 0; // Bitmap of received blocks
	
	public RequestState (Request r, Peer prev, Collection<Peer> peers)
	{
		id = r.id;
		key = r.key;
		this.prev = prev;
		nexts = new HashSet<Peer> (peers);
		nexts.remove (prev);
	}
	
	// Find the closest peer to the requested key
	public Peer closestPeer()
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
		return bestPeer; // Null if list was empty
	}
	
	// Mark a block as received, return true if it's a duplicate
	public boolean receivedBlock (int index)
	{
		boolean duplicate = (blockBitmap & 1 << index) != 0;
		blockBitmap |= 1 << index;
		return duplicate;
	}
	
	// Return true if all blocks have been received
	public boolean receivedAll()
	{
		return blockBitmap == 0xFFFFFFFF;
	}
	
	public String toString()
	{
		return new String ("request (" + id + "," + key + ")");
	}
}
