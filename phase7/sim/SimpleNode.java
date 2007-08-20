//
// A simple node (without protocol details) for high-level and topology simulations
//
package sim;

import java.util.Vector;
import java.util.LinkedList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Random;

class SimpleNode {

    boolean print_steps = false;
    static Random rand;
    boolean learning = false;
    boolean delays = false;
    int rand_interval;
    
    double location;
    public Vector<SimplePeer> neighbors; // Symmetric for undirected, outgoing when directed
    public Vector<SimplePeer> incoming;  // Incoming when directed
    public HashMap<SimpleNode,Integer> traffic_incoming;
    public HashMap<SimpleNode,Integer> traffic_outgoing;
    public HashMap<SimpleNode,Integer> init_local;       // Outgoing traffic, locally initiated
    public HashMap<SimpleNode,Integer> init_remote;      // Outgoing traffic, remotely initated
    public HashMap<SimpleNode,Integer> new_received;     // New messages received on an edge
    public HashMap<SimpleNode,Integer> new_sent;         // New messages sent on an edge
    public HashMap<SimpleNode,SimpleEstimator> estimators;
    // SimpleEstimator est;

    /* Distance time estimation
     * Let D(y) be the time to neighbor y
     * In a Kleinberg network we know that E[T|routing via y to z] = D(y) + log(|z-y|)*c
     * c is the unknown factor which we need to derive
     * Route for a while: each time we route and get a response update c (running average)
     * NB: possibly we need to estimate c depending on neighbors, varying connections to the network
     */

    /*** stats ***/
    int load = 0;
    int loadmem = 0;            // Total load seen
    boolean busy = false;       // Busy with current query?
    boolean opennet = false;    // Participate or not
    int opennet_maxpeers = 0;   // LRU-scheme to keep max this number
    int estsize;                // Size of routing estimation table

    /*** constants ***/
    public static final int LOCALORIG = 0;
    public static final int REMOTEORIG = 1;
        
    /*
     * @x: location to take (on circle)
     * @estsize: size of routing delay-estimation table (set to 0 if not used)
     */
    public SimpleNode(double x, int estsize) {
	this.location = x;
	this.estsize = estsize;
	neighbors = new Vector<SimplePeer>();
	incoming = new Vector<SimplePeer>();
	traffic_incoming = new HashMap<SimpleNode,Integer>();
	traffic_outgoing = new HashMap<SimpleNode,Integer>();
	init_local = new HashMap<SimpleNode,Integer>();
	init_remote = new HashMap<SimpleNode,Integer>();
	new_received = new HashMap<SimpleNode,Integer>();
	new_sent = new HashMap<SimpleNode,Integer>();
	// est = new SimpleEstimator(estsize);
	// rand = new Random ((int) (System.currentTimeMillis() % 10000));
	estimators = new HashMap<SimpleNode,SimpleEstimator>();
	rand = new Random(System.currentTimeMillis() % 10000);
    }

    /*
     * Enable opennet peering/attempts for this node
     *
     * @desired_peers: number of desired peers before LRU-dropping
     */
    public void enableOpennet(int desired_peers) {
	opennet = true;
	opennet_maxpeers = desired_peers;
    }

    /*
     * Disable opennet for this node
     */
    public void disableOpennet() {
	opennet = false;
    }

    /*
     * Keep incoming stats for a new query
     *
     * @inq: the query to register
     */
    public void load_incoming(SimpleQuery inq) {
	load++;
	loadmem++;
	SimpleNode n = inq.source;
	int type = inq.type;

	// Total traffic
	if(!traffic_incoming.containsKey(n)) {
	    traffic_incoming.put(n, new Integer(1));
	} else {
	    int times = traffic_incoming.get(n).intValue();
	    traffic_incoming.put(n, new Integer(times+1));
	}

	// Fresh traffic going to target
	if((inq.type==SimpleQuery.FORWARD) || (inq.type==SimpleQuery.DEADEND) || (inq.type==SimpleQuery.REJECTLOOP)) {
	    if(!new_received.containsKey(n)) {
		new_received.put(n, new Integer(1));
	    } else {
		int times = new_received.get(n).intValue();
		new_received.put(n, new Integer(times+1));
	    }
	}
    }

    /*
     * Keep outgoing stats for a query to be sent
     * 
     * fixme: change this to be called from the level sending the query
     * @n: target of the query
     * @srctype: if source is local (SimpleNode.LOCALORIG) or if remote
     *           (SimpleNode.REMOTEORIG), which is used to separate load
     *           based on locally/remotely initiated query usage
     */
    public void load_outgoing(SimpleNode n, int srctype) {
    	// Total traffic
	if(!traffic_outgoing.containsKey(n)) {
	    traffic_outgoing.put(n, new Integer(1));	    
	} else {
	    int times = traffic_outgoing.get(n).intValue();
	    traffic_outgoing.put(n, new Integer(times+1));
	}

	// Local vs Remote
	if(srctype == SimpleNode.LOCALORIG) {
	    if(!init_local.containsKey(n)) {
		init_local.put(n, new Integer(1));
	    } else {
		int times = init_local.get(n).intValue();
		init_local.put(n, new Integer(times+1));
	    }
	} else {
	    if(!init_remote.containsKey(n)) {
		init_remote.put(n, new Integer(1));
	    } else {
		int times = init_remote.get(n).intValue();
		init_remote.put(n, new Integer(times+1));
	    }
	}

	// fixme: fresh taget going to target 
    }

    /*
     * Receive a generic query
     */
    public LinkedList<SimpleQuery> recv(SimpleQuery q) {
	return route(q);
    }

    /*
     * Route from q.source to q.target
     *
     * NB: if q.dest==null we count that the query q is internally handled as starting request
     * @q: The query that specifies which target we're on to
     */
    public LinkedList<SimpleQuery> route(SimpleQuery q) {
	int htl = q.htl;       // Internal htl value
	int init;              // local or remote origin?
	SimpleLink srclink = q.link;
	load_incoming(q);

	if(q.source == this) {
	    init = SimpleNode.LOCALORIG;
	} else {
	    init = SimpleNode.REMOTEORIG;
	}

	LinkedList<SimpleQuery> result = new LinkedList<SimpleQuery>();

	if(this.location == q.target.getLocation()) {
	    result.addLast(new SimpleQuery(this,q.source,q.target,srclink,SimpleQuery.SUCCESS,0,opennet_wantPeer()));
	    load_outgoing(q.source, init);
	    return result;
	}

	if(htl == 0) {	    
            result.addLast(new SimpleQuery(this,q.source,q.target,srclink,SimpleQuery.HTLSTOP,0,false));
	    load_outgoing(q.source, init);
	    return result;
	}

	if(busy) {
	    result.addLast(new SimpleQuery(this,q.source,q.target,srclink,SimpleQuery.REJECTLOOP,htl-1,false));
	    load_outgoing(q.source, init);
	    return result;
	}

	busy = true;

	/*
	 * Available peers
	 * fixme: do this lookup in a nicer way, get called directly from a peer event
	 */
	HashSet<SimplePeer> peers = new HashSet<SimplePeer>(neighbors);	
	SimplePeer srcpeer = null;
	if((q.dest!=null) && (q.source!=this)) {  // Ignore internal and locally initiated
	    for (SimplePeer peer: neighbors) {
		if(peer.n == q.source) {
		    srcpeer = peer;
		    break;
		}
	    }
	    if(srcpeer == null) {
		System.err.println("route(): at "+ this.getLocation() +": no source peer at " + q.source.getLocation());
		EvalKleinbergLoad.printNeighbors(this);
		System.exit(0);
	    }
	}
	peers.remove(srcpeer);
	
	/*
	 * Make selection until hops left in query.
	 * If using delays, then routing on is still restricted to nodes closer
	 * to target (dont let time dominate query getting closer)
	 */	    
	int time = 0;
	while(htl>0) {
	    time++;
	    double bestdist = Double.MAX_VALUE;
	    double loc_bestdist = Double.MAX_VALUE;
	    SimplePeer bestpeer = null;
	    SimplePeer loc_bestpeer = null;
	    
	    for (SimplePeer peer: peers) {
		double dist,loc_dist;

		/*
		// Cheating for the optimal value *knowing* which neighbors have already seen query (avoid loops)
		if(peer.n.busy)
		    continue;
		*/

		// dist = peer.l.d;
		loc_dist = peer.n.distanceTo(q.target);

		if (delays) {
		    // Dist to location
		    // dist = 2*peer.l.d + est.estimate(loc_dist);		    
		    // Dist but neighbor-sensitive
		    // dist = 2*peer.l.d + estimators.get(peer.n).estimate(loc_dist);
		    // Location-based
		    dist = 2*peer.l.d + estimators.get(peer.n).estimate(q.target.getLocation());
		} else {
		    dist = loc_dist;
		}

		// Only consider a time-distance if node is physically closer
		if (loc_dist<this.distanceTo(q.target)) {
		    if (dist<bestdist) {
			bestdist = dist;
			bestpeer = peer;
		    } else if ((dist==bestdist)&&(rand.nextDouble()<0.5)) {
			bestdist = dist;
			bestpeer = peer;
		    }
		}

		// Always compute loc-best
		if (loc_dist<loc_bestdist) {
		    loc_bestdist = loc_dist;
		    loc_bestpeer = peer;
		} else if ((loc_dist==loc_bestdist)&&(rand.nextDouble()<0.5)) {
		    loc_bestdist = loc_dist;
		    loc_bestpeer = peer;
		}
	    }

	    if ((bestpeer==null) || (delays==false)) {
		bestpeer = loc_bestpeer;		
	    }

	    // Do we have an option? Otherwise the final (internal) load.
	    if (bestpeer==null) {
		result.addLast(new SimpleQuery(this,q.source,q.target,srclink,SimpleQuery.DEADEND,htl-1,false));
		load_outgoing(q.source, init);
		return result;
	    }

	    // Pass on message and monitor timing (fixme: freenet HTL heuristic)
	    result.addLast(new SimpleQuery(this,bestpeer.n,q.target,bestpeer.l,SimpleQuery.FORWARD,htl-1,false));
	    peers.remove(bestpeer);

	    // Send the packet: load outgoing, route, load incoming result
	    load_outgoing(bestpeer.n, init);
	    LinkedList<SimpleQuery> attempt = bestpeer.l.send(new SimpleQuery(this,bestpeer.n,q.target,bestpeer.l,SimpleQuery.FORWARD,htl-1,false));
	    load_incoming(attempt.getLast());
	    result.addAll(attempt);

	    // Learn from overheard/returned message (fixme: penalize when routes have been sent wrong/HTLSTOP/...)
	    if (this.learning && (attempt.getLast().type==SimpleQuery.SUCCESS)) {
		double routedist = bestpeer.n.distanceTo(q.target);
		double rtt = routeTime(result)-2*result.getLast().link.d;
		if(rtt<0) {
		    EvalKleinbergLoad.printRoute(attempt);
		    EvalKleinbergLoad.printRoute(result);		    
		    System.err.println("WTF?"); System.exit(0);
		}

		// Update estimators
		// double rtt = routeTime(attempt)-2*attempt.getLast().link.d;
		// est.update(routedist,rtt);
		// estimators.get(bestpeer.n).update(routedist,rtt);
		// estimators.get(bestpeer.n).update(q.target.getLocation(),rtt);
		estimators.get(bestpeer.n).update(q.target.getLocation(),rtt);
	    }

	    // Pass back
	    switch(attempt.getLast().type) {    	    // Did this branch succeed?
	    case SimpleQuery.SUCCESS:		
		if(q.source != this) {
		    result.addLast(new SimpleQuery(this,q.source,q.target,srclink,SimpleQuery.SUCCESS,0,q.openref));
		    load_outgoing(q.source, init);
		}

		// fixme: more advanced decision on whether to let destsampling or not
		if(opennet) {
		    opennet_destsampling(q.target);
		}

		return result;
		
	    case SimpleQuery.HTLSTOP:                       // No more Hops To Live
		if(q.source != this) {
		    result.addLast(new SimpleQuery(this,q.source,q.target,srclink,SimpleQuery.HTLSTOP,0,false));
		    load_outgoing(q.source, init);
		}
		return result;

	    default:                                        // Continue as long as possible
		htl = attempt.getLast().htl;
		break;
	    }
	}

	if(q.source != this) {                              // No more Hops To Live
	    result.addLast(new SimpleQuery(this,q.source,q.target,srclink,SimpleQuery.HTLSTOP,0,false));
	    load_outgoing(q.source, init);
	}
	return result;
    }


    /*
     * Sample destination, using opennet connections as LRU
     * fixme: support for directed edges
     *
     * 
     1. Check if we want to drop some opennet peer for a new one
     2. Check which one to drop
     3. Connect to new node (ask if still wants us)
     4. If success, disconnect the old node
     */
    void opennet_destsampling(SimpleNode dest) {
	if (this.hasNeighbor(dest) || rand.nextDouble()>0.1) {
	    return;
	}

	SimpleNode dropnode = null;
	if (!opennet_wantPeer()) {
	    dropnode = opennet_nodeToDrop();
	    if (dropnode==null) {
		return;              // Cant drop a node at the moment
	    }
	    opennet_drop(dropnode);
	}

	if (dest.opennet_acceptpeer(this)) {
	    SimpleLink link = new SimpleLink(this,dest);	
	    this.connect(dest,link,SimplePeer.t_OPENNET);
	    dest.connect(this,link,SimplePeer.t_OPENNET);
	} else {
	    System.err.println("NOT accepting peer!"); System.exit(-1); // fixme
	}
    }

    // fixme: concurrency
    public boolean opennet_wantPeer() {

	int opennet_peers=0;
	for (SimplePeer p: neighbors) {
	    if (p.t == SimplePeer.t_OPENNET) {
		opennet_peers++;
	    }
	}

	if(opennet_peers<opennet_maxpeers) {
	    return true;
	} else {
	    return false;
	}	

    }

    // LRU-drop opennet nodes (Vector.add places on end)
    public SimpleNode opennet_nodeToDrop() {
	SimpleNode dropnode=null;
	for (SimplePeer p: neighbors) {
	    if (p.t == SimplePeer.t_OPENNET) {
		dropnode = p.n;
		break;
	    }
	}
	return dropnode;
    }

    public boolean opennet_acceptpeer(SimpleNode n) {
	if (this.hasNeighbor(n)) {   	// fixme: concurrency
	    System.err.println("Error: dest target already has the peer"); System.exit(0); 
	}

        int opennet_peers=0;
        for (SimplePeer p: neighbors) {
            if (p.t == SimplePeer.t_OPENNET) {
                opennet_peers++;
            }
        }

	while (opennet_peers>=opennet_maxpeers) {
	    SimpleNode dropnode = opennet_nodeToDrop();
	    if (dropnode == null) { System.err.println("Error: cant find node to drop"); System.exit(-1); }
	    opennet_drop(dropnode);
	    opennet_peers--;
	}

	return true;
    }

    /*
     * Drop a node that is assumed to be as an opennet peer
     */
    public void opennet_drop(SimpleNode dropnode) {
	if (!hasNeighbor(dropnode)) {
	    System.err.println("Error: does not have neighbor to drop"); System.exit(-1);
	}

	if (nodeToPeer(dropnode).t!=SimplePeer.t_OPENNET) {
	    System.err.println("Error: not opennet peer"); System.exit(-1);
	}
	    
	    

	disconnect(dropnode);
	dropnode.disconnect(this);
    }


    double routeTime(LinkedList<SimpleQuery> route) {
	double t = 0;
	for (SimpleQuery q: route) {
	    t = t + q.link.d;
	}
	return t;
    }


    public double getLocation() {
	return location;
    }


    public void setLocation(double loc) {
	if((location < 0)||(location>=1)) {
	    System.err.println("setLocation(): setting location to " + location);
	    System.exit(0);
	}

	location = loc;
    }


    public double distanceTo(SimpleNode target) {
	return distanceTo(target.location);
    }


    public double distanceTo(double pos) {
	if ((pos<0) || (pos>1)) {
	    System.err.println("SimpleNode.distanceTo(): malformed distance"); System.exit(-1);
	}
	return Math.min(Math.max(location,pos)-Math.min(location,pos), Math.min(location,pos)+1.0-Math.max(location,pos));
    }


    public double distanceFrom(double from,double to) {
	return Math.min(Math.max(from,to)-Math.min(from,to), Math.min(from,to)+1.0-Math.max(from,to));
    }


    public void connect(SimpleNode target,SimpleLink link,int type) {
	if (target == this) {
	    System.err.println("SimpleNode.connect(): connecting itself"); System.exit(-1);
	}
	if (this.hasNeighbor(target)) {
	    System.err.println("SimpleNode.connect(): neighbor exists"); System.exit(-1);
	}
	if (!(type==SimplePeer.t_DARKNET || type==SimplePeer.t_OPENNET)) {
	    System.err.println("SimpleNode.connect(): unknown type " + type); System.exit(-1);
	}

	neighbors.add(new SimplePeer(target,link,type));
	target.connectIncoming(this,link,type);
	estimators.put(target,new SimpleEstimator(this.estsize));
    }


    // This is for easy book-keeping when we are evaluating a directed model
    public void connectIncoming(SimpleNode source,SimpleLink link,int type) {
	if(source == this) {
	    System.err.println("SimpleNode.connectIncoming(): connecting itself"); System.exit(-1);
	}
	if(this.hasIncoming(source)) {
	    System.err.println("SimpleNode.connectIncoming(): source exists"); System.exit(-1);
	}
	if (!(type==SimplePeer.t_DARKNET || type==SimplePeer.t_OPENNET)) {
	    System.err.println("SimpleNode.connect(): unknown type " + type); System.exit(-1);
	}

	incoming.add(new SimplePeer(source,link,SimplePeer.t_DARKNET));
    }


    /*
     * Disconnect a peer
     * note: for directed edges the source should always disconnect
     * fixme: load counting and disconnection
     */
    public void disconnect(SimpleNode target) {	
	if (nodeToPeer(target)==null) {
	    System.err.println("SimpleNode.disconnect(): peer not in neighbors");
	    System.exit(-1);
	}

	for (SimplePeer p: neighbors) {
	    if (p.n == target) {
		neighbors.remove(p);
		break;
	    }
	}

	for (SimplePeer p: incoming) {
	    if (p.n == target) {
		incoming.remove(p);
		break;
	    }
	}

	traffic_incoming.remove(target);
	traffic_outgoing.remove(target);
	init_local.remove(target);
	init_remote.remove(target);
	new_received.remove(target);
	new_sent.remove(target);
	estimators.remove(target);

    }

    public SimplePeer nodeToPeer(SimpleNode n) {
	for (SimplePeer p: neighbors) {
	    if (p.n == n) {
		return p;
	    }
	}
	return null;
    }

    public boolean hasNeighbor(SimpleNode node) {
	//return neighbors.contains(peer);
	for(SimplePeer p: neighbors) {
	    if(p.n == node)
		return true;
	}
	return false;
    }

    
    public boolean hasIncoming(SimpleNode node) {
	for(SimplePeer p: incoming) {
	    if(p.n == node)
		return true;
	}
	return false;
    }


    public int getDegree() {
	return neighbors.size();
    }


    public double logDists(double from) {
	double sum = 0;

	for (SimplePeer p: neighbors) {	    
	    if (p.n.getLocation()==from) {
		sum = sum + Math.log(distanceFrom(from,this.getLocation()));
	    } else {
		sum = sum + Math.log(distanceFrom(from,p.n.getLocation()));
	    }
	}
	return sum;
    }


    public void printQueryState(int htl) {
	System.err.print("[htl="+htl+"]");
	System.err.print("location " + location +":\t");
	System.err.print("[ ");
	for(SimplePeer p: neighbors) {
	    SimpleNode n = p.n;
	    System.err.print(n.getLocation() + " ");
	}
	System.err.print("]\t");
    }
}
