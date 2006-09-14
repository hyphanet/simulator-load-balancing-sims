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
	public final static int BUCKET_RATE = 30000; // Bytes per second
	public final static int BUCKET_SIZE = 60000; // Burst size in bytes
	
	public double location; // Routing location
	public NetworkInterface net;
	private HashMap<Integer,Peer> peers; // Look up a peer by its address
	private HashSet<Integer> recentlySeenRequests; // Request IDs
	private HashMap<Integer,MessageHandler> messageHandlers; // By ID
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
		messageHandlers = new HashMap<Integer,MessageHandler>();
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
	
	// Return true if this node is as close to the target as any peer
	private boolean closerThanPeers (double target)
	{
		double bestDist = Double.POSITIVE_INFINITY;
		for (Peer peer : peers.values()) {
			double dist = distance (target, peer.location);
			if (dist < bestDist) bestDist = dist;
		}
		return distance (target, location) <= bestDist;
	}
	
	// Decrement a request or insert's hops to live
	public int decrementHtl (int htl)
	{
		// FIXME: don't always decrement at min/max
		return htl - 1;
	}
	
	// Add a CHK to the cache
	public void cacheChk (int key)
	{
		log ("key " + key + " added to CHK cache");
		chkCache.put (key);
	}
	
	// Consider adding a CHK to the store
	public void storeChk (int key)
	{
		if (closerThanPeers (keyToLocation (key))) {
			log ("key " + key + " added to CHK store");
			chkStore.put (key);
		}
		else log ("key " + key + " not added to CHK store");
	}
	
	// Called by Peer
	public void startTimer()
	{
		if (timerRunning) return;
		// log ("starting retransmission/coalescing timer");
		Event.schedule (this, Peer.MAX_DELAY, CHECK_TIMEOUTS, null);
		timerRunning = true;
	}
	
	// Called by NetworkInterface
	public void handlePacket (Packet packet)
	{
		Peer peer = peers.get (packet.src);
		if (peer == null) log ("received packet from unknown peer");
		else peer.handlePacket (packet);
	}
	
	// Called by Peer
	public void handleMessage (Message m, Peer src)
	{
		if (src != null) log ("received " + m + " from " + src);
		if (m instanceof ChkRequest)
			handleChkRequest ((ChkRequest) m, src);
		else if (m instanceof ChkInsert)
			handleChkInsert ((ChkInsert) m, src);
		else {
			MessageHandler mh = messageHandlers.get (m.id);
			if (mh == null) log ("no message handler for " + m);
			else mh.handleMessage (m, src);
		}
	}
	
	private void handleChkRequest (ChkRequest r, Peer prev)
	{
		if (!recentlySeenRequests.add (r.id)) {
			log ("rejecting recently seen " + r);
			prev.sendMessage (new RejectedLoop (r.id));
			// Don't forward the same search back to prev
			MessageHandler mh = messageHandlers.get (r.id);
			if (mh != null) mh.removeNextHop (prev);
			return;
		}
		// Accept the search
		if (prev != null) {
			log ("accepting " + r);
			prev.sendMessage (new Accepted (r.id));
		}
		// If the key is in the store, return it
		if (chkStore.get (r.key)) {
			log ("key " + r.key + " found in CHK store");
			if (prev == null) log (r + " succeeded locally");
			else prev.sendMessage (new ChkDataFound (r.id));
			chkRequestCompleted (r.id);
			return;
		}
		log ("key " + r.key + " not found in CHK store");
		// If the key is in the cache, return it
		if (chkCache.get (r.key)) {
			log ("key " + r.key + " found in CHK cache");
			if (prev == null) log (r + " succeeded locally");
			else prev.sendMessage (new ChkDataFound (r.id));
			chkRequestCompleted (r.id);
			return;
		}
		log ("key " + r.key + " not found in CHK cache");
		// Store the request handler and forward the search
		ChkRequestHandler rh = new ChkRequestHandler (r, this, prev);
		messageHandlers.put (r.id, rh);
		rh.forwardSearch();
	}
	
	private void handleChkInsert (ChkInsert i, Peer prev)
	{
		if (!recentlySeenRequests.add (i.id)) {
			log ("rejecting recently seen " + i);
			prev.sendMessage (new RejectedLoop (i.id));
			// Don't forward the same search back to prev
			MessageHandler mh = messageHandlers.get (i.id);
			if (mh != null) mh.removeNextHop (prev);
			return;
		}
		// Accept the search
		if (prev != null) {
			log ("accepting " + i);
			prev.sendMessage (new Accepted (i.id));
		}
		// Store the insert handler and wait for a DataInsert
		ChkInsertHandler ih = new ChkInsertHandler (i, this, prev);
		messageHandlers.put (i.id, ih);
	}
	
	// Remove a completed request from the list of pending requests
	public void chkRequestCompleted (int id)
	{
		messageHandlers.remove (id);
	}
	
	// Remove a completed insert from the list of pending inserts
	public void chkInsertCompleted (int id)
	{
		messageHandlers.remove (id);
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
		ChkRequest cr = new ChkRequest (key, location);
		log ("generating " + cr);
		handleChkRequest (cr, null);
	}
	
	// Event callback
	private void generateInsert (int key)
	{
		ChkInsert ci = new ChkInsert (key, location);
		log ("generating " + ci);
		handleChkInsert (ci, null);
		handleMessage (new DataInsert (ci.id), null);
		for (int i = 0; i < 32; i++)
			handleMessage (new Block (ci.id, i), null);
	}
	
	// Event callback
	private void checkTimeouts()
	{
		// Check the peers in a random order each time
		double deadline = Double.POSITIVE_INFINITY;
		for (Peer p : peers())
			deadline = Math.min (deadline, p.checkTimeouts());
		if (deadline == Double.POSITIVE_INFINITY) {
			// log ("stopping retransmission/coalescing timer");
			timerRunning = false;
		}
		else {
			double sleep = deadline - Event.time(); // Can be < 0
			if (sleep < MIN_SLEEP) sleep = MIN_SLEEP;
			// log ("sleeping for " + sleep + " seconds");
			Event.schedule (this, sleep, CHECK_TIMEOUTS, null);
		}
	}
	
	// EventTarget interface
	public void handleEvent (int type, Object data)
	{
		switch (type) {
			case GENERATE_REQUEST:
			generateRequest ((Integer) data);
			break;
			
			case GENERATE_INSERT:
			generateInsert ((Integer)data);
			break;
			
			case CHECK_TIMEOUTS:
			checkTimeouts();
			break;
		}
	}
	
	// Each EventTarget class has its own event codes
	public final static int GENERATE_REQUEST = 1;
	public final static int GENERATE_INSERT = 2;
	private final static int CHECK_TIMEOUTS = 3;
}
