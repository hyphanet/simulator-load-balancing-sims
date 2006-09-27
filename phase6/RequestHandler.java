// The parent class of ChkRequestHandler and SskRequestHandler

import messages.*;

abstract class RequestHandler extends MessageHandler implements EventTarget
{
	public RequestHandler (Search s, Node node, Peer prev)
	{
		super (s, node, prev);
	}
	
	protected void handleAccepted (Accepted a)
	{
		if (searchState != SENT) node.log (a + " out of order");
		searchState = ACCEPTED;
		// Wait 60 seconds for a reply to the search
		Event.schedule (this, 60.0, SEARCH_TIMEOUT, next);
	}
	
	protected void handleRejectedLoop (RejectedLoop rl)
	{
		if (searchState != SENT) node.log (rl + " out of order");
		forwardSearch();
	}
	
	protected void handleRouteNotFound (RouteNotFound rnf)
	{
		if (searchState != ACCEPTED) node.log (rnf + " out of order");
		if (rnf.htl < htl) htl = rnf.htl;
		// Use the remaining htl to try another peer
		forwardSearch();
	}
	
	protected void handleDataNotFound (DataNotFound dnf)
	{
		if (searchState != ACCEPTED) node.log (dnf + " out of order");
		if (prev == null) node.log (this + " failed");
		else prev.sendMessage (dnf); // Forward the message
		finish();
	}
	
	protected void forwardSearch()
	{
		next = null;
		// If the search has run out of hops, send DataNotFound
		if (htl == 0) {
			node.log ("data not found for " + this);
			if (prev == null) node.log (this + " failed");
			else prev.sendMessage (new DataNotFound (id));
			finish();
			return;
		}
		// Forward the search to the closest remaining peer
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
		> Node.distance (target, closest))
			htl = node.decrementHtl (htl);
		node.log (this + " has htl " + htl);
		node.log ("forwarding " + this + " to " + next.address);
		next.sendMessage (makeSearchMessage());
		nexts.remove (next);
		searchState = SENT;
		// Wait 5 seconds for the next hop to accept the search
		Event.schedule (this, 5.0, ACCEPTED_TIMEOUT, next);
	}
	
	protected void finish()
	{
		searchState = COMPLETED;
		node.removeMessageHandler (id);
	}
	
	// Event callbacks
	
	protected void acceptedTimeout (Peer p)
	{
		if (p != next) return; // We've already moved on to another peer
		if (searchState != SENT) return;
		node.log (this + " accepted timeout waiting for " + p);
		forwardSearch(); // Try another peer
	}
	
	protected void searchTimeout (Peer p)
	{
		if (p != next) return; // We've already moved on to another peer
		if (searchState != ACCEPTED) return;
		node.log (this + " search timeout waiting for " + p);
		if (prev == null) node.log (this + " failed");
		finish();
	}
	
	protected void transferTimeout (Peer p)
	{
		if (searchState != TRANSFERRING) return;
		node.log (this + " transfer timeout receiving from " + p);
		if (prev == null) node.log (this + " failed");
		finish();
	}
	
	// EventTarget interface
	public void handleEvent (int type, Object data)
	{
		switch (type) {
			case ACCEPTED_TIMEOUT:
			acceptedTimeout ((Peer) data);
			break;
			
			case SEARCH_TIMEOUT:
			searchTimeout ((Peer) data);
			break;
			
			case TRANSFER_TIMEOUT:
			transferTimeout ((Peer) data);
			break;
		}
	}
	
	// Each EventTarget class has its own event codes
	protected final static int ACCEPTED_TIMEOUT = 1;
	protected final static int SEARCH_TIMEOUT = 2;
	protected final static int TRANSFER_TIMEOUT = 3;
}
