import java.util.HashMap;
import java.util.HashSet;

class Node implements EventTarget
{
	public double location; // Routing location
	public NetworkInterface net;
	private HashMap<Integer,Peer> peers; // Look up a peer by its address
	private int requestsGenerated = 0;
	private HashSet<Integer> recentlySeenRequests; // Request IDs
	private HashMap<Integer,RequestState> outstandingRequests;
	public HashSet<Double> cache; // Datastore containing keys
	
	public Node (double txSpeed, double rxSpeed)
	{
		location = Math.random();
		net = new NetworkInterface (this, txSpeed, rxSpeed);
		peers = new HashMap<Integer,Peer>();
		recentlySeenRequests = new HashSet<Integer>();
		outstandingRequests = new HashMap<Integer,RequestState>();
		cache = new HashSet<Double>();
	}
	
	public void connect (Node n, double latency)
	{
		Peer p = new Peer (this, n.net.address, n.location, latency);
		peers.put (n.net.address, p);
	}
	
	public void connectBothWays (Node n, double latency)
	{
		connect (n, latency);
		n.connect (this, latency);
	}
	
	// Returns the circular distance between two locations
	public static double distance (double a, double b)
	{
		if (a > b) return Math.min (a - b, b - a + 1.0);
		else return Math.min (b - a, a - b + 1.0);
	}
	
	// Called by NetworkInterface
	public void handlePacket (Packet packet)
	{
		Peer peer = peers.get (packet.src);
		if (peer == null) log ("unknown peer!");
		else peer.handlePacket (packet);
	}
	
	// Called by Peer
	public void handleMessage (Message m, Peer prev)
	{
		log ("received " + m);
		// FIXME: ugly
		if (m instanceof Request)
			handleRequest ((Request) m, prev);
		else if (m instanceof Response)
			handleResponse ((Response) m);
		else if (m instanceof RouteNotFound)
			handleRouteNotFound ((RouteNotFound) m);
	}
	
	private void handleRequest (Request r, Peer prev)
	{
		if (!recentlySeenRequests.add (r.id)) {
			log ("rejecting recently seen " + r);
			prev.sendMessage (new RouteNotFound (r.id));
			// Don't forward the request to prev, it's seen it
			RequestState rs = outstandingRequests.get (r.id);
			if (rs != null) rs.nexts.remove (prev);
			return;
		}
		if (cache.contains (r.key)) {
			log ("key " + r.key + " found in cache");
			if (prev == null)
				log (r + " succeeded locally");
			else prev.sendMessage (new Response (r.id, r.key));
			return;
		}
		log ("key " + r.key + " not found in cache");
		forwardRequest (new RequestState (r, prev, peers.values()));
	}
	
	private void handleResponse (Response r)
	{
		RequestState rs = outstandingRequests.remove (r.id);
		if (rs == null) {
			log ("unexpected " + r);
			return;
		}
		cache.add (r.key);
		if (rs.prev == null) log (rs + " succeeded");
		else {
			log ("forwarding " + r);
			rs.prev.sendMessage (r);
		}
	}
	
	private void handleRouteNotFound (RouteNotFound r)
	{
		RequestState rs = outstandingRequests.remove (r.id);
		if (rs == null) {
			log ("unexpected route not found " + r.id);
			return;
		}
		forwardRequest (rs);
	}
	
	private void forwardRequest (RequestState rs)
	{
		Peer next = rs.closestPeer();
		if (next == null) {
			log ("route not found for " + rs);
			if (rs.prev == null)
				log (rs + " failed");
			else rs.prev.sendMessage (new RouteNotFound (rs.id));
			return;
		}
		log ("forwarding " + rs + " to " + next.address);
		next.sendMessage (new Request (rs.id, rs.key));
		rs.nexts.remove (next);
		outstandingRequests.put (rs.id, rs);
	}
	
	private void log (String message)
	{
		Event.log (net.address + " " + message);
	}
	
	// Event callback
	private void generateRequest()
	{
		if (requestsGenerated++ > 1000) return;
		// Send a request to a random location
		Request r = new Request (0.1);
		log ("generating request " + r.id);
		handleRequest (r, null);
		// Schedule the next request
		// Event.schedule (this, Math.random(), GENERATE_REQUEST, null);
	}
	
	// EventTarget interface
	public void handleEvent (int type, Object data)
	{
		if (type == GENERATE_REQUEST) generateRequest();
	}
	
	// Each EventTarget class has its own event codes
	public final static int GENERATE_REQUEST = 1;
}
