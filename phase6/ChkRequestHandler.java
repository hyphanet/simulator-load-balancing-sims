// The state of a CHK request as stored at each node along the path

import messages.*;

class ChkRequestHandler extends MessageHandler implements EventTarget
{
	private int state = STARTED; // State of search
	private boolean[] received; // Keep track of received blocks
	private int blocksReceived = 0;
	
	public ChkRequestHandler (ChkRequest r, Node node, Peer prev)
	{
		super (r, node, prev);
		received = new boolean[32];
	}
	
	public void handleMessage (Message m, Peer src)
	{
		if (src != next) {
			node.log ("unexpected source for " + m);
			return;
		}
		if (m instanceof Accepted)
			handleAccepted ((Accepted) m);
		else if (m instanceof RejectedLoop)
			handleRejectedLoop ((RejectedLoop) m);
		else if (m instanceof RouteNotFound)
			handleRouteNotFound ((RouteNotFound) m);
		else if (m instanceof DataNotFound)
			handleDataNotFound ((DataNotFound) m);
		else if (m instanceof ChkDataFound)
			handleChkDataFound ((ChkDataFound) m);
		else if (m instanceof Block)
			handleBlock ((Block) m);
		else node.log ("unexpected type for " + m);
	}
	
	private void handleAccepted (Accepted a)
	{
		if (state != SENT) node.log (a + " out of order");
		state = ACCEPTED;
		// Wait 60 seconds for a reply to the search
		Event.schedule (this, 60.0, SEARCH_TIMEOUT, next);
	}
	
	private void handleRejectedLoop (RejectedLoop rl)
	{
		if (state != SENT) node.log (rl + " out of order");
		forwardSearch();
	}
	
	private void handleRouteNotFound (RouteNotFound rnf)
	{
		if (state != ACCEPTED) node.log (rnf + " out of order");
		if (rnf.htl < htl) htl = rnf.htl;
		// Use the remaining htl to try another peer
		forwardSearch();
	}
	
	private void handleDataNotFound (DataNotFound dnf)
	{
		if (state != ACCEPTED) node.log (dnf + " out of order");
		if (prev == null) node.log (this + " failed");
		else prev.sendMessage (dnf); // Forward the message
		finish();
	}
	
	private void handleChkDataFound (ChkDataFound df)
	{
		if (state != ACCEPTED) node.log (df + " out of order");
		state = TRANSFERRING;
		if (prev != null) prev.sendMessage (df); // Forward the message
		// Wait for the transfer to complete (FIXME: check real timeout)
		Event.schedule (this, 120.0, TRANSFER_TIMEOUT, next);
	}
	
	private void handleBlock (Block b)
	{
		if (state != TRANSFERRING) node.log (b + " out of order");
		if (received[b.index]) return; // Ignore duplicates
		received[b.index] = true;
		blocksReceived++;
		// Forward the block
		if (prev != null) {
			node.log ("forwarding " + b);
			prev.sendMessage (b);
		}
		// If the transfer is complete, cache the data
		if (blocksReceived == 32) {
			node.cacheChk (key);
			if (prev == null) node.log (this + " succeeded");
			finish();
		}
	}
	
	public void forwardSearch()
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
		> Node.distance (target, closest)) {
			htl = node.decrementHtl (htl);
			node.log (this + " has htl " + htl);
		}
		node.log ("forwarding " + this + " to " + next.address);
		next.sendMessage (new ChkRequest (id, key, closest, htl));
		nexts.remove (next);
		state = SENT;
		// Wait 5 seconds for the next hop to accept the search
		Event.schedule (this, 5.0, ACCEPTED_TIMEOUT, next);
	}
	
	private void finish()
	{
		state = COMPLETED;
		node.chkRequestCompleted (id);
	}
	
	public String toString()
	{
		return new String ("CHK request (" + id + "," + key + ")");
	}
	
	// Event callbacks
	
	private void acceptedTimeout (Peer p)
	{
		if (p != next) return; // We've already moved on to another peer
		if (state != SENT) return;
		node.log (this + " accepted timeout waiting for " + p);
		forwardSearch(); // Try another peer
	}
	
	private void searchTimeout (Peer p)
	{
		if (p != next) return; // We've already moved on to another peer
		if (state != ACCEPTED) return;
		node.log (this + " search timeout waiting for " + p);
		if (prev == null) node.log (this + " failed");
		finish();
	}
	
	private void transferTimeout (Peer p)
	{
		if (state != TRANSFERRING) return;
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
	private final static int ACCEPTED_TIMEOUT = 1;
	private final static int SEARCH_TIMEOUT = 2;
	private final static int TRANSFER_TIMEOUT = 3;
}
