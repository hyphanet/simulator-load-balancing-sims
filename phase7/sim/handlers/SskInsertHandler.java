// The state of an SSK insert as stored at each node along the path

package sim.handlers;
import sim.*;
import sim.messages.*;
import java.util.HashSet;

public class SskInsertHandler extends MessageHandler implements EventTarget
{
	private SskPubKey pubKey = null; 
	private int data; // The data being inserted
	
	public SskInsertHandler (SskInsert i, Node node,
				Peer prev, boolean needPubKey)
	{
		super (i, node, prev);
		data = i.data;
		if (!needPubKey) pubKey = new SskPubKey (id, key);
	}
	
	public void start()
	{
		if (pubKey == null) {
			// Wait 10 seconds for the previous hop to send the key
			Event.schedule (this, 10.0, KEY_TIMEOUT, null);
		}
		else {
			checkCollision();
			forwardSearch();
		}
	}
	
	// Check whether an older version of the data is already stored
	private void checkCollision()
	{
		Integer old = node.fetchSsk (key);
		if (old != null && old.intValue() != data) {
			node.log (this + " collided");
			if (prev == null) node.log (this + " collided locally");
			else prev.sendMessage (new SskDataFound (id, old));
			// Continue inserting the old data
			data = old;
			return;
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
			else if (m instanceof RejectedOverload)
				handleRejectedOverload ((RejectedOverload) m);
			else if (m instanceof RouteNotFound)
				handleRouteNotFound ((RouteNotFound) m);
			else if (m instanceof SskDataFound)
				handleCollision ((SskDataFound) m);
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
		checkCollision();
		forwardSearch();
	}
	
	private void handleSskAccepted (SskAccepted sa)
	{
		if (searchState != SENT) node.log (sa + " out of order");
		searchState = ACCEPTED;
		next.successNotOverload(); // Reset the backoff length
		// Wait 60 seconds for a reply to the search
		Event.schedule (this, 60.0, SEARCH_TIMEOUT, next);
		// Send the public key if requested
		if (sa.needPubKey) next.sendMessage (pubKey);
	}
	
	private void handleCollision (SskDataFound sdf)
	{
		if (searchState != ACCEPTED) node.log (sdf + " out of order");
		if (prev == null) node.log (this + " collided");
		else prev.sendMessage (sdf); // Forward the message
		data = sdf.data; // Is this safe?
	}
	
	private void handleInsertReply (InsertReply ir)
	{
		if (searchState != ACCEPTED) node.log (ir + " out of order");
		if (prev == null) {
			node.log (this + " succeeded");
			node.increaseSearchRate();
		}
		else prev.sendMessage (ir); // Forward the message
		finish();
	}
	
	protected void sendReply()
	{
		if (prev == null) {
			node.log (this + " succeeded");
			node.increaseSearchRate();
		}
		else prev.sendMessage (new InsertReply (id));
	}
	
	protected Search makeSearchMessage()
	{
		return new SskInsert (id, key, data, closest, htl);
	}
	
	protected void scheduleAcceptedTimeout (Peer next)
	{
		Event.schedule (this, 10.0, ACCEPTED_TIMEOUT, next);
	}
	
	protected void finish()
	{
		searchState = COMPLETED;
		node.cachePubKey (key);
		node.storePubKey (key);
		node.cacheSsk (key, data);
		node.storeSsk (key, data);
		node.removeMessageHandler (id);
	}
	
	public String toString()
	{
		return new String ("SSK insert (" +id+ "," +key+ "," +data+")");
	}
	
	// Event callback
	private void keyTimeout()
	{
		if (searchState != STARTED) return;
		node.log (this + " key timeout for " + prev);
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
	
	private final static int KEY_TIMEOUT = 1;
	private final static int ACCEPTED_TIMEOUT = 2;
	private final static int SEARCH_TIMEOUT = 3;
}
