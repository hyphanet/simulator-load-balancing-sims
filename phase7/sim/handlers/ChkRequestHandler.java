// The state of a CHK request as stored at each node along the path

package sim.handlers;
import sim.*;
import sim.messages.*;

public class ChkRequestHandler extends RequestHandler
{
	private Block[] blocks; // Store incoming blocks for forwarding
	private int blocksReceived = 0;
	
	public ChkRequestHandler (ChkRequest r, Node node, Peer prev)
	{
		super (r, node, prev);
		blocks = new Block[32];
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
		if (searchState != ACCEPTED) node.log (df + " out of order");
		searchState = TRANSFERRING;
		if (prev != null) {
			// Forward the message & all previously received blocks
			prev.sendMessage (df);
			for (int i = 0; i < 32; i++)
				if (blocks[i] != null)
					prev.sendMessage (blocks[i]);
		}
		// Wait for the transfer to complete (FIXME: check real timeout)
		Event.schedule (this, 120.0, TRANSFER_TIMEOUT, next);
	}
	
	private void handleBlock (Block b)
	{
		if (searchState != TRANSFERRING) node.log (b + " out of order");
		if (blocks[b.index] != null) return; // Ignore duplicates
		blocks[b.index] = b;
		blocksReceived++;
		if (searchState == TRANSFERRING) return; // Forward it later
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
