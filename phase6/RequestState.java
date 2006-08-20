// The state of an outstanding request, stored at each node along the path

import java.util.HashSet;
import java.util.Collection;
import messages.Request;

class RequestState
{
	public final int id; // The unique ID of the request
	public final int key; // The requested key
	public final Peer prev; // The previous hop of the request
	public final HashSet<Peer> nexts; // Possible next hops
	
	public RequestState (Request r, Peer prev, Collection<Peer> peers)
	{
		id = r.id;
		key = r.key;
		this.prev = prev;
		nexts = new HashSet<Peer> (peers);
		if (prev != null) nexts.remove (prev);
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
	
	public String toString()
	{
		return new String ("request (" + id + "," + key + ")");
	}
}
