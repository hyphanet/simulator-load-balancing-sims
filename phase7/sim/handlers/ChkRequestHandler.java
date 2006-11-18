// The state of a CHK request as stored at each node along the path

package sim.handlers;
import sim.*;
import sim.messages.*;

public class ChkRequestHandler extends RequestHandler
{
	private boolean[] blocks; // Keep track of blocks received
	private int blocksReceived = 0;
	
	public ChkRequestHandler (ChkRequest r, Node node, Peer prev)
	{
		super (r, node, prev);
		blocks = new boolean[32];
	}
	
	public void handleMessage (Message m, Peer src)
	{
		if (src != next)
			node.log ("unexpected source for " + m);
		else if (m instanceof Accepted)
			handleAccepted ((Accepted) m);
		else if (m instanceof RejectedLoop)
			handleRejectedLoop ((RejectedLoop) m);
		else if (m instanceof RejectedOverload)
			handleRejectedOverload ((RejectedOverload) m);
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
		if (searchState != ACCEPTED) node.log (df + " out of order");
		searchState = TRANSFERRING;
		if (prev != null) prev.sendMessage (df); // Forward the message
		// If we have all the blocks and the headers, cache the data
		if (blocksReceived == 32) {
			node.cacheChk (key);
			if (prev == null) {
				node.log (this + "succeeded");
				node.increaseSearchRate();
			}
			finish();
		}
		// Wait for the transfer to complete (FIXME: check real timeout)
		else Event.schedule (this, 120.0, TRANSFER_TIMEOUT, next);
	}
	
	private void handleBlock (Block b)
	{
		if (searchState != TRANSFERRING) node.log (b + " out of order");
		if (blocks[b.index]) return; // Ignore duplicates
		blocks[b.index] = true;
		blocksReceived++;
		// Forward the block
		if (prev != null) {
			node.log ("forwarding " + b);
			prev.sendMessage (b);
		}
		// If we have all the blocks and the headers, cache the data
		if (blocksReceived == 32 && searchState == TRANSFERRING) {
			node.cacheChk (key);
			if (prev == null) {
				node.log (this + " succeeded");
				node.increaseSearchRate();
			}
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
