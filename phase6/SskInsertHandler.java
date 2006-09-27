// The state of an SSK insert as stored at each node along the path

import java.util.HashSet;
import messages.*;

class SskInsertHandler extends MessageHandler implements EventTarget
{
	private int searchState = STARTED; // searchState of search
	private SskPubKey pubKey = null; 
	
	public SskInsertHandler (SskInsert i, Node node,
				Peer prev, boolean needPubKey)
	{
		super (i, node, prev);
		// Wait 10 seconds for the previous hop to send the public key
		if (needPubKey) Event.schedule (this, 10.0, KEY_TIMEOUT, null);
		else {
			pubKey = new SskPubKey (id, key);
			node.cacheSsk (key);
			node.storeSsk (key);
			forwardSearch();
		}
	}
	
	public void handleMessage (Message m, Peer src)
	{
		if (src == prev) {
			if (m instanceof SskPubKey)
				handleSskPubKey ((SskPubKey) m);
			else node.log ("unexpected type for " + m);
		}
		else if (src == next) {
			if (m instanceof SskAccepted)
				handleSskAccepted ((SskAccepted) m);
			else if (m instanceof RejectedLoop)
				handleRejectedLoop ((RejectedLoop) m);
			else if (m instanceof RouteNotFound)
				handleRouteNotFound ((RouteNotFound) m);
			else if (m instanceof InsertReply)
				handleInsertReply ((InsertReply) m);
			else node.log ("unexpected type for " + m);
		}
		else node.log ("unexpected source for " + m);
	}
	
	private void handleSskPubKey (SskPubKey pk)
	{
		if (searchState != STARTED) node.log (pk + " out of order");
		pubKey = pk;
		node.cachePubKey (key);
		node.cacheSsk (key);
		node.storeSsk (key);
		forwardSearch();
	}
	
	private void handleSskAccepted (SskAccepted sa)
	{
		if (searchState != SENT) node.log (sa + " out of order");
		searchState = ACCEPTED;
		// Wait 60 seconds for a reply to the search
		Event.schedule (this, 60.0, SEARCH_TIMEOUT, next);
		// Send the public key if requested
		if (sa.needPubKey) next.sendMessage (pubKey);
	}
	
	private void handleRejectedLoop (RejectedLoop rl)
	{
		if (searchState != SENT) node.log (rl + " out of order");
		forwardSearch();
	}
	
	private void handleRouteNotFound (RouteNotFound rnf)
	{
		if (searchState != ACCEPTED) node.log (rnf + " out of order");
		if (rnf.htl < htl) htl = rnf.htl;
		// Use the remaining htl to try another peer
		forwardSearch();
	}
	
	private void handleInsertReply (InsertReply ir)
	{
		if (searchState != ACCEPTED) node.log (ir + " out of order");
		if (prev == null) node.log (this + " succeeded");
		else prev.sendMessage (ir); // Forward the message
		finish();
	}
	
	public void forwardSearch()
	{
		next = null;
		// If the search has run out of hops, send InsertReply
		if (htl == 0) {
			node.log (this + " has no hops left");
			if (prev == null) node.log (this + " succeeded");
			else prev.sendMessage (new InsertReply (id));
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
		// Wait 10 seconds for the next hop to accept the search
		Event.schedule (this, 10.0, ACCEPTED_TIMEOUT, next);
	}
	
	private void finish()
	{
		searchState = COMPLETED;
		node.removeMessageHandler (id);
	}
	
	protected Search makeSearchMessage()
	{
		return new SskInsert (id, key, closest, htl);
	}
	
	public String toString()
	{
		return new String ("SSK insert (" + id + "," + key + ")");
	}
	
	// Event callbacks
	
	private void keyTimeout()
	{
		if (searchState != STARTED) return;
		node.log (this + " key timeout waiting for " + prev);
		finish();
	}
	
	private void acceptedTimeout (Peer p)
	{
		if (p != next) return; // We've already moved on to another peer
		if (searchState != SENT) return;
		node.log (this + " accepted timeout waiting for " + p);
		forwardSearch(); // Try another peer
	}
	
	private void searchTimeout (Peer p)
	{
		if (p != next) return; // We've already moved on to another peer
		if (searchState != ACCEPTED) return;
		node.log (this + " search timeout waiting for " + p);
		if (prev == null) node.log (this + " failed");
		finish();
	}
	
	// EventTarget interface
	public void handleEvent (int type, Object data)
	{
		switch (type) {
			case KEY_TIMEOUT:
			keyTimeout();
			break;
			
			case ACCEPTED_TIMEOUT:
			acceptedTimeout ((Peer) data);
			break;
			
			case SEARCH_TIMEOUT:
			searchTimeout ((Peer) data);
			break;			
		}
	}
	
	// Each EventTarget class has its own event codes
	private final static int KEY_TIMEOUT = 1;
	private final static int ACCEPTED_TIMEOUT = 2;
	private final static int SEARCH_TIMEOUT = 3;
}
