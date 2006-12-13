package sim;
import sim.generators.Client;
import sim.handlers.*;
import sim.messages.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;

public class Node implements EventTarget
{
	public final static boolean LOG = false;
	
	// Coarse-grained retransmission timer
	public final static double RETX_TIMER = 0.1; // Seconds
	
	// Flow control
	public static boolean useTokens = false;
	public static boolean useBackoff = false;
	public static boolean useThrottle = false;
	public final static int FLOW_TOKENS = 50; // Shared by all peers
	public final static double DELAY_DECAY = 0.99; // Exp moving average
	public final static double MAX_DELAY = 2.0; // Reject all, seconds
	public final static double HIGH_DELAY = 1.0; // Reject some, seconds
	
	// Statistics (for requests and inserts combined)
	public static int succeededLocally = 0;
	public static int succeededRemotely = 0;
	public static int failed = 0;
	
	public double location; // Routing location
	public NetworkInterface net;
	private HashMap<Integer,Peer> peers; // Look up a peer by its address
	private HashSet<Integer> recentlySeenRequests; // Request IDs
	private HashMap<Integer,MessageHandler> messageHandlers; // By ID
	private LruCache<Integer> chkStore;
	private LruCache<Integer> chkCache;
	private LruMap<Integer,Integer> sskStore; // SSKs can collide, use a Map
	private LruMap<Integer,Integer> sskCache;
	private LruCache<Integer> pubKeyStore; // SSK public keys
	private LruCache<Integer> pubKeyCache;
	private boolean decrementMaxHtl = false;
	private boolean decrementMinHtl = false;
	public TokenBucket bandwidth; // Bandwidth limiter
	private boolean timerRunning = false;
	private int spareTokens = FLOW_TOKENS; // Tokens not allocated to a peer
	private double delay = 0.0; // Delay caused by congestion or b/w limiter
	private LinkedList<Search> searchQueue;
	private SearchThrottle searchThrottle;
	private HashSet<Peer> availablePeers; // Peers with outgoing tokens
	
	public Node (double txSpeed, double rxSpeed)
	{
		this (Math.random(), txSpeed, rxSpeed);
	}
	
	public Node (double location, double txSpeed, double rxSpeed)
	{
		this.location = location;
		net = new NetworkInterface (this, txSpeed, rxSpeed);
		peers = new HashMap<Integer,Peer>();
		recentlySeenRequests = new HashSet<Integer>();
		messageHandlers = new HashMap<Integer,MessageHandler>();
		chkStore = new LruCache<Integer> (16000);
		chkCache = new LruCache<Integer> (16000);
		sskStore = new LruMap<Integer,Integer> (16000);
		sskCache = new LruMap<Integer,Integer> (16000);
		pubKeyStore = new LruCache<Integer> (16000);
		pubKeyCache = new LruCache<Integer> (16000);
		if (Math.random() < 0.5) decrementMaxHtl = true;
		if (Math.random() < 0.25) decrementMinHtl = true;
		bandwidth = new TokenBucket (40000, 60000);
		searchQueue = new LinkedList<Search>();
		if (useTokens) {
			// Allocate flow control tokens after a short delay
			Event.schedule (this, Math.random() * 0.1,
					ALLOCATE_TOKENS, null);
			availablePeers = new HashSet<Peer>();
		}
		if (useThrottle) searchThrottle = new SearchThrottle();
	}
	
	// Return true if a connection was added, false if already connected
	public boolean connect (Node n, double latency)
	{
		if (n == this) return false;
		if (peers.containsKey (n.net.address)) return false;
		Peer p = new Peer (this, n.net.address, n.location, latency);
		peers.put (n.net.address, p);
		return true;
	}
	
	public boolean connectBothWays (Node n, double latency)
	{
		if (connect (n, latency)) return n.connect (this, latency);
		else return false;
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
		if ((htl == Search.MAX_HTL && !decrementMaxHtl)
		|| (htl == 1 && !decrementMinHtl)) return htl;
		else return htl - 1;
	}
	
	// Return true if the node appears to be overloaded
	private boolean shouldRejectSearch()
	{
		if (delay > MAX_DELAY) return true;
		if (delay > HIGH_DELAY) {
			double p = (delay-HIGH_DELAY) / (MAX_DELAY-HIGH_DELAY);
			if (Math.random() < p) return true;
		}
		return false;
	}
	
	// Reject a request or insert if the node appears to be overloaded
	private boolean rejectIfOverloaded (Peer prev, int id)
	{
		if (prev == null) return false;
		if (shouldRejectSearch()) {
			prev.sendMessage (new RejectedOverload (id, true));
			return true;
		}
		return false;
	}
	
	// Reject a request or insert if its search ID has already been seen
	private boolean rejectIfRecentlySeen (Peer prev, int id)
	{
		if (recentlySeenRequests.add (id)) return false;
		
		if (LOG) log ("rejecting recently seen search " + id);
		prev.sendMessage (new RejectedLoop (id));
		if (useTokens) allocateToken (prev);
		// Don't forward the same search back to prev
		MessageHandler mh = messageHandlers.get (id);
		if (mh != null) mh.removeNextHop (prev);
		return true;
	}
	
	// Add a CHK to the cache
	public void cacheChk (int key)
	{
		if (LOG) log ("key " + key + " added to CHK cache");
		chkCache.put (key);
	}
	
	// Consider adding a CHK to the store
	public void storeChk (int key)
	{
		if (closerThanPeers (keyToLocation (key))) {
			if (LOG) log ("key " + key + " added to CHK store");
			chkStore.put (key);
		}
		else if (LOG) log ("key " + key + " not added to CHK store");
	}
	
	// Retrieve an SSK from the cache or the store
	public Integer fetchSsk (int key)
	{
		Integer data = sskStore.get (key);
		if (data == null) return sskCache.get (key);
		else return data;
	}
	
	// Add an SSK to the cache
	public void cacheSsk (int key, int value)
	{
		if (LOG) log ("key " + key + " added to SSK cache");
		sskCache.put (key, value);
	}
	
	// Consider adding an SSK to the store
	public void storeSsk (int key, int value)
	{
		if (closerThanPeers (keyToLocation (key))) {
			if (LOG) log ("key " + key + " added to SSK store");
			sskStore.put (key, value);
		}
		else if (LOG) log ("key " + key + " not added to SSK store");
	}
	
	// Add a public key to the cache
	public void cachePubKey (int key)
	{
		if (LOG) log ("public key " + key + " added to cache");
		pubKeyCache.put (key);
	}
	
	// Consider adding a public key to the store
	public void storePubKey (int key)
	{
		if (closerThanPeers (keyToLocation (key))) {
			if (LOG) log ("public key " + key + " added to store");
			pubKeyStore.put (key);
		}
		else if (LOG) log ("public key " + key + " not added to store");
	}
	
	// Called by Peer to start the retransmission timer
	public void startTimer()
	{
		if (timerRunning) return;
		timerRunning = true;
		if (LOG) log ("starting retransmission timer");
		Event.schedule (this, RETX_TIMER, CHECK_TIMEOUTS, null);
	}
	
	// Called by Peer to transmit a packet for the first time
	public void sendPacket (Packet p)
	{
		// Update the bandwidth limiter
		bandwidth.remove (p.size);
		// Update the average bandwidth delay
		if (p.messages != null) {
			double now = Event.time();
			for (Message m : p.messages) {
				if (LOG) log ("sending " + m + " to " + p.dest);
				double d = now - m.deadline;
				delay *= DELAY_DECAY;
				delay += d * (1.0 - DELAY_DECAY);
			}
			if (LOG) log ("average message delay " + delay);
		}
		// Send the packet
		net.sendPacket (p);
	}
	
	// Called by Peer to retransmit a packet
	public void resendPacket (Packet p)
	{
		// Update the bandwidth limiter
		bandwidth.remove (p.size);
		// Send the packet
		net.sendPacket (p);
	}
	
	// Called by NetworkInterface
	public void handlePacket (Packet p)
	{
		Peer peer = peers.get (p.src);
		if (peer == null) {
			if (LOG) log ("received packet from unknown peer");
		}
		else peer.handlePacket (p);
	}
	
	// Called by Peer
	public void handleMessage (Message m, Peer src)
	{
		if (src != null && LOG) log ("received " + m + " from " + src);
		if (m instanceof Token)
			handleToken ((Token) m, src);
		else if (m instanceof ChkRequest)
			handleChkRequest ((ChkRequest) m, src);
		else if (m instanceof ChkInsert)
			handleChkInsert ((ChkInsert) m, src);
		else if (m instanceof SskRequest)
			handleSskRequest ((SskRequest) m, src);
		else if (m instanceof SskInsert)
			handleSskInsert ((SskInsert) m, src);
		else {
			MessageHandler mh = messageHandlers.get (m.id);
			if (mh == null) {
				if (LOG) log ("no message handler for " + m);
			}
			else mh.handleMessage (m, src);
		}
	}
	
	private void handleToken (Token t, Peer prev)
	{
		prev.addTokensOut (t.id); // t.id is the number of tokens
	}
	
	private void handleChkRequest (ChkRequest r, Peer prev)
	{
		if ((useBackoff || useThrottle)
		&& rejectIfOverloaded (prev, r.id)) return;
		if (useTokens && !getToken (prev)) return;
		if (rejectIfRecentlySeen (prev, r.id)) return;
		// Accept the search
		if (prev != null) {
			if (LOG) log ("accepting " + r);
			prev.sendMessage (new Accepted (r.id));
		}
		// If the data is in the store, return it
		if (chkStore.get (r.key)) {
			if (LOG) log ("key " + r.key + " found in CHK store");
			if (prev == null) {
				if (LOG) log (r + " succeeded locally");
				succeededLocally++;
			}
			else {
				prev.sendMessage (new ChkDataFound (r.id));
				for (int i = 0; i < 32; i++)
					prev.sendMessage (new Block (r.id, i));
			}
			if (useTokens) allocateToken (prev);
			return;
		}
		if (LOG) log ("key " + r.key + " not found in CHK store");
		// If the data is in the cache, return it
		if (chkCache.get (r.key)) {
			if (LOG) log ("key " + r.key + " found in CHK cache");
			if (prev == null) {
				if (LOG) log (r + " succeeded locally");
				succeededLocally++;
			}
			else {
				prev.sendMessage (new ChkDataFound (r.id));
				for (int i = 0; i < 32; i++)
					prev.sendMessage (new Block (r.id, i));
			}
			if (useTokens) allocateToken (prev);
			return;
		}
		if (LOG) log ("key " + r.key + " not found in CHK cache");
		// Store the request handler and forward the search
		ChkRequestHandler rh = new ChkRequestHandler (r, this, prev);
		messageHandlers.put (r.id, rh);
		rh.start();
	}
	
	private void handleChkInsert (ChkInsert i, Peer prev)
	{
		if ((useBackoff || useThrottle)
		&& rejectIfOverloaded (prev, i.id)) return;
		if (useTokens && !getToken (prev)) return;
		if (rejectIfRecentlySeen (prev, i.id)) return;
		// Accept the search
		if (prev != null) {
			if (LOG) log ("accepting " + i);
			prev.sendMessage (new Accepted (i.id));
		}
		// Store the insert handler and wait for a DataInsert
		ChkInsertHandler ih = new ChkInsertHandler (i, this, prev);
		messageHandlers.put (i.id, ih);
		ih.start();
	}
	
	private void handleSskRequest (SskRequest r, Peer prev)
	{
		if ((useBackoff || useThrottle)
		&& rejectIfOverloaded (prev, r.id)) return;
		if (useTokens && !getToken (prev)) return;
		if (rejectIfRecentlySeen (prev, r.id)) return;
		// Look up the public key
		boolean pub = pubKeyStore.get (r.key) || pubKeyCache.get(r.key);
		if (LOG) {
			if (pub) log ("public key " +r.key+ " found in cache");
			else log ("public key " +r.key+ " not found in cache");
		}
		// Accept the search
		if (prev != null) {
			if (LOG) log ("accepting " + r);
			prev.sendMessage (new Accepted (r.id));
		}
		// If the data is in the store, return it
		Integer data = sskStore.get (r.key);
		if (pub && data != null) {
			if (LOG) log ("key " + r.key + " found in SSK store");
			if (prev == null) {
				if (LOG) log (r + " succeeded locally");
				succeededLocally++;
			}
			else {
				prev.sendMessage (new SskDataFound (r.id,data));
				if (r.needPubKey)
					prev.sendMessage
						(new SskPubKey (r.id, r.key));
			}
			if (useTokens) allocateToken (prev);
			return;
		}
		if (LOG) log ("key " + r.key + " not found in SSK store");
		// If the data is in the cache, return it
		data = sskCache.get (r.key);
		if (pub && data != null) {
			if (LOG) log ("key " + r.key + " found in SSK cache");
			if (prev == null) {
				if (LOG) log (r + " succeeded locally");
				succeededLocally++;
			}
			else {
				prev.sendMessage (new SskDataFound (r.id,data));
				if (r.needPubKey)
					prev.sendMessage
						(new SskPubKey (r.id, r.key));
			}
			if (useTokens) allocateToken (prev);
			return;
		}
		if (LOG) log ("key " + r.key + " not found in SSK cache");
		// Store the request handler and forward the search
		SskRequestHandler rh = new SskRequestHandler (r,this,prev,!pub);
		messageHandlers.put (r.id, rh);
		rh.start();
	}
	
	private void handleSskInsert (SskInsert i, Peer prev)
	{
		if ((useBackoff || useThrottle)
		&& rejectIfOverloaded (prev, i.id)) return;
		if (useTokens && !getToken (prev)) return;
		if (rejectIfRecentlySeen (prev, i.id)) return;
		// Look up the public key
		boolean pub = pubKeyStore.get (i.key) || pubKeyCache.get(i.key);
		if (LOG) {
			if (pub) log ("public key " +i.key+ " found in cache");
			else log ("public key " +i.key+ " not found in cache");
		}
		// Accept the search
		if (prev != null) {
			if (LOG) log ("accepting " + i);
			prev.sendMessage (new SskAccepted (i.id, !pub));
		}
		// Store the insert handler and possibly wait for the pub key
		SskInsertHandler ih = new SskInsertHandler (i,this,prev,!pub);
		messageHandlers.put (i.id, ih);
		ih.start();
	}
	
	public void increaseSearchRate()
	{
		if (useThrottle) searchThrottle.increaseRate();
	}
	
	public void decreaseSearchRate()
	{
		if (useThrottle) searchThrottle.decreaseRate();
	}
	
	public void removeMessageHandler (int id)
	{
		MessageHandler mh = messageHandlers.remove (id);
		if (mh == null) {
			if (LOG) log ("no message handler to remove for " + id);
		}
		else {
			if (LOG) log ("removing message handler for " + id);
			if (useTokens) allocateToken (mh.prev);
		}
	}
	
	// Check whether the peer sendng a request or insert has enough tokens
	private boolean getToken (Peer p)
	{
		if (p == null) {
			if (spareTokens == 0) {
				// The client will have to wait
				if (LOG) log ("not enough tokens");
				return false;
			}
			spareTokens--;
			return true;
		}
		else {
			if (p.getTokensIn() == 0) {
				// This indicates a misbehaving sender
				if (LOG) log ("WARNING: not enough tokens");
				return false;
			}
			p.removeTokensIn (1);
			return true;
		}
	}
	
	// Give another token to the peer whose request/insert just completed
	private void allocateToken (Peer p)
	{
		if (p == null) spareTokens++;
		else p.addTokensIn (1);
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
	
	// Add a search to the queue
	private void addToSearchQueue (Search s)
	{
		searchQueue.add (s);
		if (useThrottle) {
			if (LOG) log (searchQueue.size() +" searches in queue");
			if (searchQueue.size() > 1) return; // Already waiting
			double now = Event.time();
			double wait = searchThrottle.delay (now);
			if (wait <= 0.0) sendSearch();
			else {
				if (LOG) log ("throttled for "+wait+" seconds");
				Event.schedule (this, wait, SEND_SEARCH, null);
			}
		}
		else sendSearch();
	}
	
	// Remove the first search from the queue and send it
	private void sendSearch()
	{
		if (useTokens && availablePeers.isEmpty()) {
			if (LOG) log ("blocked");
			return;
		}
		Search s = searchQueue.poll();
		// Inform the client that the search has left the queue
		if (s.client != null) s.client.searchStarted (s);
		if (s instanceof ChkRequest)
			handleChkRequest ((ChkRequest) s, null);
		else if (s instanceof ChkInsert) {
			handleChkInsert ((ChkInsert) s, null);
			handleMessage (new DataInsert (s.id), null);
			for (int i = 0; i < 32; i++)
				handleMessage (new Block (s.id, i), null);
		}
		else if (s instanceof SskRequest)
			handleSskRequest ((SskRequest) s, null);
		else if (s instanceof SskInsert) {
			pubKeyCache.put (s.key);
			handleSskInsert ((SskInsert) s, null);
		}
		if (useThrottle) {
			double now = Event.time();
			searchThrottle.sent (now);
			if (searchQueue.isEmpty()) return;
			double wait = searchThrottle.delay (now);
			if (LOG) log ("throttled for " + wait + " seconds");
			Event.schedule (this, wait, SEND_SEARCH, null);
		}
	}
	
	public void addAvailablePeer (Peer p)
	{
		boolean blocked = availablePeers.isEmpty();
		availablePeers.add (p);
		if (blocked && !searchQueue.isEmpty()) sendSearch();
	}
	
	public void removeAvailablePeer (Peer p)
	{
		availablePeers.remove (p);
	}
	
	public void generateChkRequest (int key)
	{
		ChkRequest cr = new ChkRequest (key, location);
		if (LOG) log ("generating " + cr);
		addToSearchQueue (cr);
	}
	
	public void generateChkInsert (int key, Client c)
	{
		ChkInsert ci = new ChkInsert (key, location, c);
		if (LOG) log ("generating " + ci);
		addToSearchQueue (ci);
	}
	
	public void generateSskRequest (int key)
	{
		SskRequest sr = new SskRequest (key, location, true);
		if (LOG) log ("generating " + sr);
		addToSearchQueue (sr);
	}
	
	public void generateSskInsert (int key, int value, Client c)
	{
		SskInsert si = new SskInsert (key, value, location, c);
		if (LOG) log ("generating " + si);
		addToSearchQueue (si);
	}
	
	private void checkTimeouts()
	{
		boolean stopTimer = true;
		for (Peer p : peers()) if (p.checkTimeouts()) stopTimer = false;
		if (stopTimer) {
			if (LOG) log ("stopping retransmission timer");
			timerRunning = false;
		}
		else Event.schedule (this, RETX_TIMER, CHECK_TIMEOUTS, null);
	}
	
	// Allocate all flow control tokens at startup
	private void allocateTokens()
	{
		// Rounding error in your favour - collect 50 tokens
		int tokensPerPeer = FLOW_TOKENS / (peers.size() + 1);
		for (Peer p : peers.values()) {
			spareTokens -= tokensPerPeer;
			p.addTokensIn (tokensPerPeer);
		}
	}
	
	// EventTarget interface
	public void handleEvent (int type, Object data)
	{
		switch (type) {
			case REQUEST_CHK:
			generateChkRequest ((Integer) data);
			break;
			
			case INSERT_CHK:
			generateChkInsert ((Integer) data, null);
			break;
			
			case REQUEST_SSK:
			generateSskRequest ((Integer) data);
			break;
			
			case INSERT_SSK:
			generateSskInsert ((Integer) data, 0, null);
			break;
			
			case SSK_COLLISION:
			generateSskInsert ((Integer) data, 1, null);
			break;
			
			case CHECK_TIMEOUTS:
			checkTimeouts();
			break;
			
			case ALLOCATE_TOKENS:
			allocateTokens();
			break;
			
			case SEND_SEARCH:
			sendSearch();
			break;
		}
	}
	
	public final static int REQUEST_CHK = 1;
	public final static int INSERT_CHK = 2;
	public final static int REQUEST_SSK = 3;
	public final static int INSERT_SSK = 4;
	public final static int SSK_COLLISION = 5;
	private final static int CHECK_TIMEOUTS = 6;
	private final static int ALLOCATE_TOKENS = 7;
	private final static int SEND_SEARCH = 8;
}
