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
    Random rand;
    
    double location;
    public Vector<SimpleNode> neighbors; // Symmetric for undirected, outgoing when directed
    public Vector<SimpleNode> incoming;  // Incoming when directed
    public HashMap<SimpleNode,Integer> traffic_incoming;
    public HashMap<SimpleNode,Integer> traffic_outgoing;
    public HashMap<SimpleNode,Integer> init_local;       // Outgoing traffic, locally initiated
    public HashMap<SimpleNode,Integer> init_remote;      // Outgoing traffic, remotely initated
    public HashMap<SimpleNode,Integer> new_received;     // New messages received on an edge
    public HashMap<SimpleNode,Integer> new_sent;         // New messages sent on an edge

    /*** stats ***/
    int load = 0;
    int loadmem = 0;     // Internal 
    boolean busy = false;

    /*** constants ***/
    public static final int LOCALORIG = 0;
    public static final int REMOTEORIG = 1;
        
    public SimpleNode(double x) {
	this.location = x;
	neighbors = new Vector<SimpleNode>();
	incoming = new Vector<SimpleNode>();
	traffic_incoming = new HashMap<SimpleNode,Integer>();
	traffic_outgoing = new HashMap<SimpleNode,Integer>();
	init_local = new HashMap<SimpleNode,Integer>();
	init_remote = new HashMap<SimpleNode,Integer>();
	new_received = new HashMap<SimpleNode,Integer>();
	new_sent = new HashMap<SimpleNode,Integer>();
	rand = new Random ((int) (System.currentTimeMillis() % 10000));
    }

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

    public LinkedList<SimpleQuery> route(SimpleQuery q) {
	int htl = q.htl;       // Internal htl value
	int init;              // local or remote origin?
	//load_incoming(q.source);
	load_incoming(q);

	if(q.source == this) {
	    init = SimpleNode.LOCALORIG;
	} else {
	    init = SimpleNode.REMOTEORIG;
	}

	if(print_steps)
	    printQueryState(htl);

	LinkedList<SimpleQuery> result = new LinkedList<SimpleQuery>();

	if(this.location == q.target.getLocation()) {
	    result.addLast(new SimpleQuery(this,q.source,q.target,SimpleQuery.SUCCESS,0));
	    load_outgoing(q.source, init);
	    return result;
	}

	if(htl == 0) {	    
            result.addLast(new SimpleQuery(this,q.source,q.target,SimpleQuery.HTLSTOP,0));
	    load_outgoing(q.source, init);
	    return result;
	}

	if(busy) {
	    result.addLast(new SimpleQuery(this,q.source,q.target,SimpleQuery.REJECTLOOP,htl-1));
	    load_outgoing(q.source, init);
	    return result;
	}

	busy = true;

	HashSet<SimpleNode> peers = new HashSet<SimpleNode>(neighbors);	
	peers.remove(q.source);
	
	int time = 0;
	while(htl>0) {

	    if(print_steps && (time>0))
		printQueryState(htl);
	    time++;
	    
	    double bestdist = Double.MAX_VALUE;
	    SimpleNode bestpeer = null;
	    
	    for (SimpleNode peer: peers) {
		double dist = peer.distanceTo(q.target);
		if(dist < bestdist) {
		    bestdist = dist;
		    bestpeer = peer;
		} else if (dist == bestdist) {
		    if(rand.nextDouble()<0.5) {
			bestdist = dist;
			bestpeer = peer;
		    }
		}
	    }
	    
	    if(bestpeer == null) {
		result.addLast(new SimpleQuery(this,q.source,q.target,SimpleQuery.DEADEND,htl-1));
		load_outgoing(q.source, init);
		return result;
	    }

	    result.addLast(new SimpleQuery(this,bestpeer,q.target,SimpleQuery.FORWARD,htl-1));	
	    peers.remove(bestpeer);

	    if(print_steps)
		System.err.println("sending to " + bestpeer.getLocation() + "(attempt #"+time+")");

	    // fixme: Freenet HTL heuristic
	    load_outgoing(bestpeer, init);
	    LinkedList<SimpleQuery> attempt = bestpeer.route(new SimpleQuery(this,bestpeer,q.target,SimpleQuery.FORWARD,htl-1));
	    load_incoming(attempt.getLast());

	    result.addAll(attempt);

	    switch(attempt.getLast().type) {    	    // Did this branch succeed?
	    case SimpleQuery.SUCCESS:		
		if(q.source != this) {
		    result.addLast(new SimpleQuery(this,q.source,q.target,SimpleQuery.SUCCESS,0));
		    load_outgoing(q.source, init);
		}
		return result;
		
	    case SimpleQuery.HTLSTOP:
		if(q.source != this) {
		    result.addLast(new SimpleQuery(this,q.source,q.target,SimpleQuery.HTLSTOP,0));
		    load_outgoing(q.source, init);
		}
		return result;

	    default:
		htl = attempt.getLast().htl;
		break;
	    }
	}

	if(q.source != this) {	    
	    result.addLast(new SimpleQuery(this,q.source,q.target,SimpleQuery.HTLSTOP,0));
	    load_outgoing(q.source, init);
	}
	return result;
    }

    public double getLocation() {
	return location;
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

    public void connect(SimpleNode target) {
	if (target == this) {
	    System.err.println("SimpleNode.connect(): connecting itself"); System.exit(-1);
	}

	if (neighbors.contains(target)) {
	    System.err.println("SimpleNode.connect(): neighbors exists"); System.exit(-1);
	}
	neighbors.add(target);
	target.connectIncoming(this);
    }

    // This is for easy book-keeping when we are evaluating a directed model
    public void connectIncoming(SimpleNode source) {
	if(source == this) {
	    System.err.println("SimpleNode.connectIncoming(): connecting itself"); System.exit(-1);
	}
	if(incoming.contains(source)) {
	    System.err.println("SimpleNode.connectIncoming(): source exists"); System.exit(-1);
	}
	incoming.add(source);
    }

    public boolean hasNeighbor(SimpleNode peer) {
	return neighbors.contains(peer);
    }

    public int getDegree() {
	return neighbors.size();
    }

    public void printQueryState(int htl) {
	System.err.print("[htl="+htl+"]");
	System.err.print("location " + location +":\t");
	System.err.print("[ ");
	for(SimpleNode n: neighbors) {
	    System.err.print(n.getLocation() + " ");
	}
	System.err.print("]\t");
    }

}
