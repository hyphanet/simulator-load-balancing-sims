import java.util.HashMap;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.Collections;
import messages.*;

class Node implements EventTarget
{
	public final static int STORE_SIZE = 10; // Max number of keys in store
	public final static int CACHE_SIZE = 10; // Max number of keys in cache
	public final static double MIN_SLEEP = 0.01; // Seconds
	public final static double SHORT_SLEEP = 0.05; // Poll the bw limiter
	
	// Token bucket bandwidth limiter
	public final static int BUCKET_RATE = 15000; // Bytes per second
	public final static int BUCKET_SIZE = 60000; // Burst size in bytes
	
	public double location; // Routing location
	public NetworkInterface net;
	private HashMap<Integer,Peer> peers; // Look up a peer by its address
	private HashSet<Integer> recentlySeenRequests; // Request IDs
	private HashMap<Integer,ChkRequestHandler> chkRequests; // By ID
	private LruCache<Integer> chkStore; // CHK datastore
	private LruCache<Integer> chkCache; // CHK datacache
	public TokenBucket bandwidth; // Bandwidth limiter
	private boolean timerRunning = false; // Is the timer running?
	
	public Node (double txSpeed, double rxSpeed)
	{
		location = Math.random();
		net = new NetworkInterface (this, txSpeed, rxSpeed);
		peers = new HashMap<Integer,Peer>();
		recentlySeenRequests = new HashSet<Integer>();
		chkRequests = new HashMap<Integer,ChkRequestHandler>();
		chkStore = new LruCache<Integer> (STORE_SIZE);
		chkCache = new LruCache<Integer> (CACHE_SIZE);
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
	
	// Add a CHK to the cache and consider adding it to the store
	public void storeChk (int key)
	{
		log ("key " + key + " added to CHK cache");
		chkCache.put (key);
		// Add the key to the store if this node is as close to the
		// key's location as any of its peers
		if (isClosest (keyToLocation (key))) {
			log ("key " + key + " added to CHK store");
			chkStore.put (key);
		}
	}
	
	// Return true if this node is as close to the target as any peer
	private boolean isClosest (double target)
	{
		double bestDist = Double.POSITIVE_INFINITY;
		for (Peer peer : peers.values()) {
			double dist = distance (target, peer.location);
			if (dist < bestDist) bestDist = dist;
		}
		return distance (target, location) <= bestDist;
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
	public void handleMessage (Message m, Peer src)
	{
		log ("received " + m);
		if (m instanceof ChkRequest) {
			if (handleChkRequest ((ChkRequest) m, src))
				chkRequests.remove (m.id); // Completed
		}
		else {
			ChkRequestHandler rh = chkRequests.get (m.id);
			if (rh == null) log ("no request handler for " + m);
			else if (rh.handleMessage (m, src))
				chkRequests.remove (m.id); // Completed
		}
	}
	
	private boolean handleChkRequest (ChkRequest r, Peer prev)
	{
		if (!recentlySeenRequests.add (r.id)) {
			log ("rejecting recently seen " + r);
			prev.sendMessage (new RejectedLoop (r.id));
			// Optimisation: the previous hop has already seen
			// this request, so don't ask it in the future
			ChkRequestHandler rh = chkRequests.get (r.id);
			if (rh != null) rh.removeNextHop (prev);
			return false; // Request not completed
		}
		// Accept the request
		if (prev != null) prev.sendMessage (new Accepted (r.id));
		// If the key is in the store, return it
		if (chkStore.get (r.key)) {
			log ("key " + r.key + " found in CHK store");
			if (prev == null) log (r + " succeeded locally");
			else {
				prev.sendMessage (new ChkDataFound (r.id));
				for (int i = 0; i < 32; i++)
					prev.sendMessage (new Block (r.id, i));
			}
			return true; // Request completed
		}
		log ("key " + r.key + " not found in CHK store");
		// If the key is in the cache, return it
		if (chkCache.get (r.key)) {
			log ("key " + r.key + " found in CHK cache");
			if (prev == null) log (r + " succeeded locally");
			else {
				prev.sendMessage (new ChkDataFound (r.id));
				for (int i = 0; i < 32; i++)
					prev.sendMessage (new Block (r.id, i));
			}
			return true; // Request completed
		}
		log ("key " + r.key + " not found in CHK cache");
		// Forward the request and store the request state
		ChkRequestHandler rh = new ChkRequestHandler (r, this, prev);
		chkRequests.put (r.id, rh);
		return rh.forwardRequest();
	}
	
	// Return the list of peers in a random order
	public ArrayList<Peer> peers()
	{
		ArrayList<Peer> copy = new ArrayList<Peer> (peers.values());
		Collections.shuffle (copy);
		return copy;
	}
	
	public void log (String message)
	{
		Event.log (net.address + " " + message);
	}
	
	// Event callback
	private void generateRequest (int key)
	{
		ChkRequest r = new ChkRequest (key);
		log ("generating " + r);
		handleChkRequest (r, null);
	}
	
	// Event callback
	private void checkTimeouts()
	{
		// Check the peers in a random order each time
		double deadline = Double.POSITIVE_INFINITY;
		for (Peer p : peers())
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
