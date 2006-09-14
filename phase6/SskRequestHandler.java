// The state of an SSK request as stored at each node along the path

import messages.*;

class SskRequestHandler extends RequestHandler
{
	private boolean needPubKey; // Ask the next hop for the public key?
	private SskPubKey pubKey = null;
	private SskDataFound data = null;
	
	public SskRequestHandler (SskRequest r, Node node,
				Peer prev, boolean needPubKey)
	{
		super (r, node, prev);
		this.needPubKey = needPubKey;
		if (!needPubKey) pubKey = new SskPubKey (id, key);
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
		else if (m instanceof SskDataFound)
			handleSskDataFound ((SskDataFound) m);
		else if (m instanceof SskPubKey)
			handleSskPubKey ((SskPubKey) m);
		else node.log ("unexpected type for " + m);
	}
	
	private void handleSskDataFound (SskDataFound df)
	{
		if (state != ACCEPTED) node.log (df + " out of order");
		data = df;
		if (pubKey == null) return; // Keep waiting
		if (prev == null) node.log (this + " succeeded");
		else {
			prev.sendMessage (data);
			if (needPubKey) prev.sendMessage (pubKey);
		}
		node.cachePubKey (key);
		node.cacheSsk (key);
		finish();
	}
	
	private void handleSskPubKey (SskPubKey pk)
	{
		if (state != ACCEPTED) node.log (pk + " out of order");
		pubKey = pk;
		if (data == null) return; // Keep waiting
		if (prev == null) node.log (this + " succeeded");
		else {
			prev.sendMessage (data);
			if (needPubKey) prev.sendMessage (pubKey);
		}
		node.cachePubKey (key);
		node.cacheSsk (key);
		finish();
	}
	
	protected Search makeSearchMessage()
	{
		return new SskRequest (id, key, closest, htl, pubKey == null);
	}
	
	public String toString()
	{
		return new String ("SSK request (" + id + "," + key + ")");
	}
}