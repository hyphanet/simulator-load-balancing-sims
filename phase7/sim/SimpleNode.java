//
// A simple node (without protocol details) for high-level and topology simulations
//
package sim;

import java.util.Vector;
import java.util.LinkedList;
import java.util.HashSet;
import java.util.Random;

class SimpleNode {
    
    double location;
    public Vector<SimpleNode> neighbors; // Symmetric for undirected, outgoing when directed
    public Vector<SimpleNode> incoming;  // Incoming when directed
    Random rand;
    boolean print_steps = false;

    /*** stats ***/
    int load = 0;
    int loadmem = 0;
    int busy = 0;
    int initiated = 0;
        
    public SimpleNode(double x) {
	this.location = x;
	neighbors = new Vector<SimpleNode>();
	incoming = new Vector<SimpleNode>();
	rand = new Random ((int) (System.currentTimeMillis() % 10000));
    }

    public LinkedList<SimpleQuery> route(SimpleQuery q) {
	load++;              // Received query from peer
	loadmem++;
	int htl = q.htl;     // Internal htl value
	if(print_steps)
	    printQueryState(htl);

	LinkedList<SimpleQuery> result = new LinkedList<SimpleQuery>();

	if(this.location == q.target.getLocation()) {
	    result.addLast(new SimpleQuery(this,q.target,SimpleQuery.SUCCESS,0));
	    return result;
	}

	if(htl == 0) {	    
            result.addLast(new SimpleQuery(this,q.target,SimpleQuery.HTLSTOP,0));
	    return result;
	}

	if(busy != 0) {
	    result.addLast(new SimpleQuery(this,q.target,SimpleQuery.REJECTLOOP,htl-1));
	    return result;
	}

	busy = 1;

	HashSet<SimpleNode> peers = new HashSet<SimpleNode>(neighbors);	
	peers.remove(q.peer);
	
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
	    
	    if(bestpeer == null) { 	    // Dead end
		result.addLast(new SimpleQuery(this,q.target,SimpleQuery.DEADEND,htl-1));
		return result;
	    }

	    result.addLast(new SimpleQuery(this,q.target,SimpleQuery.FORWARD,htl-1));	
	    peers.remove(bestpeer);

	    if(print_steps)
		System.err.println("sending to " + bestpeer.getLocation() + "(attempt #"+time+")");

	                  // fixme: Freenet HTL heuristic
	    LinkedList<SimpleQuery> attempt = bestpeer.route(new SimpleQuery(this,q.target,SimpleQuery.FORWARD,htl-1));
	    load++;       // Received answer
	    loadmem++;
	    result.addAll(attempt);

	    switch(attempt.getLast().type) {

	    case SimpleQuery.SUCCESS:		
		result.addLast(new SimpleQuery(this,q.peer,SimpleQuery.SUCCESS,0));
		return result;
		
	    case SimpleQuery.HTLSTOP:
		result.addLast(new SimpleQuery(this,q.peer,SimpleQuery.HTLSTOP,0));
		return result;

	    default:
		htl = attempt.getLast().htl;
		break;
	    }
	}

	result.addLast(new SimpleQuery(this,q.target,SimpleQuery.HTLSTOP,0));
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

    public void disconnect(SimpleNode target) {
	if (target == this) {
	    System.err.println("SimpleNode.disconnect(): disconnecting itself"); System.exit(-1);
	}

	if (!neighbors.remove(target)) {
	    System.err.println("SimpleNode.disconnect(): neighbor not exists"); System.exit(-1);
	}
    }

    public boolean hasNeighbor(SimpleNode peer) {
	return neighbors.contains(peer);
    }

    public int getDegree() {
	return neighbors.size();
    }

    public void printNeighbors() {
	System.out.print("[ ");
	for(SimpleNode n: neighbors) {
	    System.out.print(n.getLocation() + " ");
	}
	System.out.print("]");
    }

    public void printIncoming() {
	System.out.print("I[ ");
	for(SimpleNode n: incoming) {
	    System.out.print(n.getLocation() + " ");
	}
	System.out.print("]");
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
