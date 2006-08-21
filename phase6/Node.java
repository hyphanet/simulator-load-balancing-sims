import java.util.HashMap;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.Collections;
import messages.*;

class Node implements EventTarget
{
	public final static int STORE_SIZE = 10; // Max number of keys in store
	public final static double MIN_SLEEP = 0.01; // Seconds
	public final static double SHORT_SLEEP = 0.05; // Poll the bw limiter
	
	// Token bucket bandwidth limiter
	public final static int BUCKET_RATE = 20000; // Bytes per second
	public final static int BUCKET_SIZE = 40000; // Burst size in bytes
	
	public double location; // Routing location
	public NetworkInterface net;
	private HashMap<Integer,Peer> peers; // Look up a peer by its address
	private HashSet<Integer> recentlySeenRequests; // Request IDs
	private HashMap<Integer,RequestState> outstandingRequests; // By ID
	public LruCache<Integer> cache; // Datastore containing keys
	public TokenBucket bandwidth; // Bandwidth limiter
	private boolean timerRunning = false; // Is the timer running?
	
	public Node (double txSpeed, double rxSpeed)
	{
		location = Math.random();
		net = new NetworkInterface (this, txSpeed, rxSpeed);
		peers = new HashMap<Integer,Peer>();
		recentlySeenRequests = new HashSet<Integer>();
		outstandingRequests = new HashMap<Integer,RequestState>();
		cache = new LruCache<Integer> (STORE_SIZE);
		bandwidth = new TokenBucket (BUCKET_RATE, BUCKET_SIZE);
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
	
	// Calculate the circular distance between two locations
	public static double distance (double a, double b)
	{
		if (a > b) return Math.min (a - b, b - a + 1.0);
		else return Math.min (b - a, a - b + 1.0);
	}
	
	// Convert an integer key to a routing location
	public static double keyToLocation (int key)
	{
		return key / (double) Integer.MAX_VALUE;
	}
	
	// Convert a routing location to an integer key
	public static int locationToKey (double location)
	{
		return (int) (location * Integer.MAX_VALUE);
	}
	
	// Called by Peer
	public void startTimer()
	{
		if (timerRunning) return;
		log ("starting retransmission/coalescing timer");
		Event.schedule (this, Peer.MAX_DELAY, CHECK_TIMEOUTS, null);
		timerRunning = true;
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
		if (m instanceof Request) handleRequest ((Request) m, prev);
		else {
			RequestState rs = outstandingRequests.get (m.id);
			if (rs == null) log ("unexpected " + m);
			else if (m instanceof Response)
				handleResponse ((Response) m, rs);
			else if (m instanceof RouteNotFound)
				handleRouteNotFound ((RouteNotFound) m, rs);
		}
	}
	
	private void handleRequest (Request r, Peer prev)
	{
		if (!recentlySeenRequests.add (r.id)) {
			log ("rejecting recently seen " + r);
			prev.sendMessage (new RouteNotFound (r.id));
			// Optimisation: prev has seen the request, so remove
			// it from the list of potential next hops
			RequestState rs = outstandingRequests.get (r.id);
			if (rs != null) rs.nexts.remove (prev);
			return;
		}
		if (cache.get (r.key)) {
			log ("key " + r.key + " found in cache");
			if (prev == null) log (r + " succeeded locally");
			else for (int i = 0; i < 32; i++)
				prev.sendBlock (new Response (r.id, i));
			return;
		}
		log ("key " + r.key + " not found in cache");
		forwardRequest (new RequestState (r, prev, shufflePeers()));
	}
	
	private void handleResponse (Response r, RequestState rs)
	{
		rs.state = RequestState.TRANSFERRING;
		if (rs.receivedBlock (r.index)) return; // Ignore duplicates
		if (rs.receivedAll()) {
			cache.put (rs.key);
			if (rs.prev == null) log (rs + " succeeded");
			outstandingRequests.remove (rs.id);
		}
		// Forward the block
		if (rs.prev != null) {
			log ("forwarding " + r);
			rs.prev.sendBlock (r);
		}
	}
	
	private void handleRouteNotFound (RouteNotFound r, RequestState rs)
	{
		forwardRequest (rs);
	}
	
	private void forwardRequest (RequestState rs)
	{
		Peer next = rs.closestPeer();
		if (next == null) {
			log ("route not found for " + rs);
			if (rs.prev == null) log (rs + " failed");
			else rs.prev.sendMessage (new RouteNotFound (rs.id));
			outstandingRequests.remove (rs.id);
		}
		else {
			log ("forwarding " + rs + " to " + next.address);
			next.sendMessage (new Request (rs.id, rs.key));
			rs.nexts.remove (next);
			outstandingRequests.put (rs.id, rs);
		}
	}
	
	// Return the list of peers in a random order
	private ArrayList<Peer> shufflePeers()
	{
		ArrayList<Peer> copy = new ArrayList<Peer> (peers.values());
		Collections.shuffle (copy);
		return copy;
	}
	
	private void log (String message)
	{
		Event.log (net.address + " " + message);
	}
	
	// Event callback
	private void generateRequest (int key)
	{
		Request r = new Request (key);
		log ("generating request " + r.id);
		handleRequest (r, null);
	}
	
	// Event callback
	private void checkTimeouts()
	{
		// Check the peers in a random order each time
		double deadline = Double.POSITIVE_INFINITY;
		for (Peer p : shufflePeers())
			deadline = Math.min (deadline, p.checkTimeouts());
		if (deadline == Double.POSITIVE_INFINITY) {
			log ("stopping retransmission/coalescing timer");
			timerRunning = false;
		}
		else {
			double sleep = deadline - Event.time(); // Can be < 0
			if (sleep < MIN_SLEEP) sleep = MIN_SLEEP;
			log ("sleeping for " + sleep + " seconds");
			Event.schedule (this, sleep, CHECK_TIMEOUTS, null);
		}
	}
	
	// EventTarget interface
	public void handleEvent (int type, Object data)
	{
		if (type == GENERATE_REQUEST) generateRequest ((Integer) data);
		else if (type == CHECK_TIMEOUTS) checkTimeouts();
	}
	
	// Each EventTarget class has its own event codes
	public final static int GENERATE_REQUEST = 1;
	public final static int CHECK_TIMEOUTS = 2;
}
