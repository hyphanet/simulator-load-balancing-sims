// The state of a CHK request as stored at each node along the path

import messages.*;

class ChkRequestHandler extends RequestHandler
{
	private boolean[] received; // Keep track of received blocks
	private int blocksReceived = 0;
	
	public ChkRequestHandler (ChkRequest r, Node node, Peer prev)
	{
		super (r, node, prev);
		received = new boolean[32];
		forwardSearch();
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
	
	protected Search makeSearchMessage()
	{
		return new ChkRequest (id, key, closest, htl);
	}
	
	public String toString()
	{
		return new String ("CHK request (" + id + "," + key + ")");
	}
}
