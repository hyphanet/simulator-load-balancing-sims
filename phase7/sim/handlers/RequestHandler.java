// The parent class of ChkRequestHandler and SskRequestHandler

package sim.handlers;
import sim.*;
import sim.messages.*;

public abstract class RequestHandler extends MessageHandler
					implements EventTarget
{
	public RequestHandler (Search s, Node node, Peer prev)
	{
		super (s, node, prev);
	}
	
	public void start()
	{
		forwardSearch();
	}
	
	protected void handleAccepted (Accepted a)
	{
		if (searchState != SENT && LOG) node.log (a + " out of order");
		searchState = ACCEPTED;
		next.successNotOverload(); // Reset the backoff length
		// Wait 60 seconds for a reply to the search
		Event.schedule (this, 60.0, SEARCH_TIMEOUT, next);
	}
	
	protected void handleDataNotFound (DataNotFound dnf)
	{
		if (searchState != ACCEPTED && LOG)
			node.log (dnf + " out of order");
		if (prev == null) node.log (this + " failed (dnf)");
		else prev.sendMessage (dnf); // Forward the message
		finish();
	}
	
	protected void sendReply()
	{
		if (prev == null) node.log (this + " failed (dnf)");
		else prev.sendMessage (new DataNotFound (id));
	}
	
	protected void scheduleAcceptedTimeout (Peer next)
	{
		Event.schedule (this, 5.0, ACCEPTED_TIMEOUT, next);
	}
	
	protected void finish()
	{
		searchState = COMPLETED;
		node.removeMessageHandler (id);
	}
	
	// Event callback
	protected void transferTimeout (Peer p)
	{
		if (searchState != TRANSFERRING) return;
		if (LOG) node.log (this + " transfer timeout from " + p);
		if (prev == null) node.log (this + " failed (xfer)");
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
	
	protected final static int ACCEPTED_TIMEOUT = 1;
	protected final static int SEARCH_TIMEOUT = 2;
	protected final static int TRANSFER_TIMEOUT = 3;
}
