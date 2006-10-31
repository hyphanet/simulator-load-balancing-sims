package sim;
import sim.handlers.*;
import sim.messages.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.Collections;

public class Node implements EventTarget
{
	// Token bucket bandwidth limiter
	public final static int BUCKET_RATE = 30000; // Bytes per second
	public final static int BUCKET_SIZE = 60000; // Burst size in bytes
	
	public double location; // Routing location
	public NetworkInterface net;
	private HashMap<Integer,Peer> peers; // Look up a peer by its address
	private HashSet<Integer> recentlySeenRequests; // Request IDs
	private HashMap<Integer,MessageHandler> messageHandlers; // By ID
	private LruCache<Integer> chkStore;
	private LruCache<Integer> chkCache;
	private LruMap<Integer,Integer> sskStore; // SSKs can collide
	private LruMap<Integer,Integer> sskCache;
	private LruCache<Integer> pubKeyCache; // SSK public keys
	private boolean decrementMaxHtl = false;
	private boolean decrementMinHtl = false;
	public TokenBucket bandwidth; // Bandwidth limiter
	private boolean timerRunning = false; // Is the retx timer running?
	
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
		chkStore = new LruCache<Integer> (10);
		chkCache = new LruCache<Integer> (10);
		sskStore = new LruMap<Integer,Integer> (10);
		sskCache = new LruMap<Integer,Integer> (10);
		pubKeyCache = new LruCache<Integer> (10);
		if (Math.random() < 0.5) decrementMaxHtl = true;
		if (Math.random() < 0.25) decrementMinHtl = true;
		bandwidth = new TokenBucket (BUCKET_RATE, BUCKET_SIZE);
	}
	
	// Return true if a connection was added, false if already connected
	public boolean connect (Node n, double latency)
	{
		if (n == this) return false;
		if (peers.containsKey (n.net.address)) return false;
		// log ("adding peer " + n.net.address);
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
		log ("key " + key + " added to SSK cache");
		sskCache.put (key, value);
	}
	
	// Consider adding an SSK to the store
	public void storeSsk (int key, int value)
	{
		if (closerThanPeers (keyToLocation (key))) {
			log ("key " + key + " added to SSK store");
			sskStore.put (key, value);
		}
		else log ("key " + key + " not added to SSK store");
	}
	
	// Add a public key to the cache
	public void cachePubKey (int key)
	{
		log ("public key " + key + " added to cache");
		pubKeyCache.put (key);
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
		else if (m instanceof SskRequest)
			handleSskRequest ((SskRequest) m, src);
		else if (m instanceof SskInsert)
			handleSskInsert ((SskInsert) m, src);
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
		// If the data is in the store, return it
		if (chkStore.get (r.key)) {
			log ("key " + r.key + " found in CHK store");
			if (prev == null) log (r + " succeeded locally");
			else {
				prev.sendMessage (new ChkDataFound (r.id));
				for (int i = 0; i < 32; i++)
					prev.sendMessage (new Block (r.id, i));
			}
			return;
		}
		log ("key " + r.key + " not found in CHK store");
		// If the data is in the cache, return it
		if (chkCache.get (r.key)) {
			log ("key " + r.key + " found in CHK cache");
			if (prev == null) log (r + " succeeded locally");
			else {
				prev.sendMessage (new ChkDataFound (r.id));
				for (int i = 0; i < 32; i++)
					prev.sendMessage (new Block (r.id, i));
			}
			return;
		}
		log ("key " + r.key + " not found in CHK cache");
		// Store the request handler and forward the search
		ChkRequestHandler rh = new ChkRequestHandler (r, this, prev);
		messageHandlers.put (r.id, rh);
		rh.start();
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
		ih.start();
	}
	
	private void handleSskRequest (SskRequest r, Peer prev)
	{
		if (!recentlySeenRequests.add (r.id)) {
			log ("rejecting recently seen " + r);
			prev.sendMessage (new RejectedLoop (r.id));
			// Don't forward the same search back to prev
			MessageHandler mh = messageHandlers.get (r.id);
			if (mh != null) mh.removeNextHop (prev);
			return;
		}
		// Look up the public key
		boolean pub = pubKeyCache.get (r.key);
		if (pub) log ("public key " + r.key + " found in cache");
		else log ("public key " + r.key + " not found in cache");
		// Accept the search
		if (prev != null) {
			log ("accepting " + r);
			prev.sendMessage (new Accepted (r.id));
		}
		// If the data is in the store, return it
		Integer data = sskStore.get (r.key);
		if (pub && data != null) {
			log ("key " + r.key + " found in SSK store");
			if (prev == null) log (r + " succeeded locally");
			else {
				prev.sendMessage (new SskDataFound (r.id,data));
				if (r.needPubKey)
					prev.sendMessage
						(new SskPubKey (r.id, r.key));
			}
			return;
		}
		log ("key " + r.key + " not found in SSK store");
		// If the data is in the cache, return it
		data = sskCache.get (r.key);
		if (pub && data != null) {
			log ("key " + r.key + " found in SSK cache");
			if (prev == null) log (r + " succeeded locally");
			else {
				prev.sendMessage (new SskDataFound (r.id,data));
				if (r.needPubKey)
					prev.sendMessage
						(new SskPubKey (r.id, r.key));
			}
			return;
		}
		log ("key " + r.key + " not found in SSK cache");
		// Store the request handler and forward the search
		SskRequestHandler rh = new SskRequestHandler (r,this,prev,!pub);
		messageHandlers.put (r.id, rh);
		rh.start();
	}
	
	private void handleSskInsert (SskInsert i, Peer prev)
	{
		if (!recentlySeenRequests.add (i.id)) {
			log ("rejecting recently seen " + i);
			prev.sendMessage (new RejectedLoop (i.id));
			// Don't forward the same search back to prev
			MessageHandler mh = messageHandlers.get (i.id);
			if (mh != null) mh.removeNextHop (prev);
			return;
		}
		// Look up the public key
		boolean pub = pubKeyCache.get (i.key);
		if (pub) log ("public key " + i.key + " found in cache");
		else log ("public key " + i.key + " not found in cache");
		// Accept the search
		if (prev != null) {
			log ("accepting " + i);
			prev.sendMessage (new SskAccepted (i.id, !pub));
		}
		// Store the insert handler and possibly wait for the pub key
		SskInsertHandler ih = new SskInsertHandler (i,this,prev,!pub);
		messageHandlers.put (i.id, ih);
		ih.start();
	}
	
	public void removeMessageHandler (int id)
	{
		MessageHandler mh = messageHandlers.remove (id);
		if (mh == null) log ("no message handler to remove for " + id);
		else log ("removing message handler for " + id);
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
	
	// Event callbacks
	
	private void generateChkRequest (int key)
	{
		ChkRequest cr = new ChkRequest (key, location);
		log ("generating " + cr);
		handleChkRequest (cr, null);
	}
	
	private void generateChkInsert (int key)
	{
		ChkInsert ci = new ChkInsert (key, location);
		log ("generating " + ci);
		handleChkInsert (ci, null);
		handleMessage (new DataInsert (ci.id), null);
		for (int i = 0; i < 32; i++)
			handleMessage (new Block (ci.id, i), null);
	}
	
	private void generateSskRequest (int key)
	{
		SskRequest sr = new SskRequest (key, location, true);
		log ("generating " + sr);
		handleSskRequest (sr, null);
	}
	
	private void generateSskInsert (int key, int value)
	{
		SskInsert si = new SskInsert (key, value, location);
		log ("generating " + si);
		pubKeyCache.put (key);
		handleSskInsert (si, null);
	}
	
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
			double sleep = Math.max (deadline - Event.time(), 0.0);
			// log ("sleeping for " + sleep + " seconds");
			Event.schedule (this, sleep, CHECK_TIMEOUTS, null);
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
			generateChkInsert ((Integer) data);
			break;
			
			case REQUEST_SSK:
			generateSskRequest ((Integer) data);
			break;
			
			case INSERT_SSK:
			generateSskInsert ((Integer) data, 0);
			break;
			
			case SSK_COLLISION:
			generateSskInsert ((Integer) data, 1);
			
			case CHECK_TIMEOUTS:
			checkTimeouts();
			break;
		}
	}
	
	// Each EventTarget class has its own event codes
	public final static int REQUEST_CHK = 1;
	public final static int INSERT_CHK = 2;
	public final static int REQUEST_SSK = 3;
	public final static int INSERT_SSK = 4;
	public final static int SSK_COLLISION = 5;
	private final static int CHECK_TIMEOUTS = 6;
}
