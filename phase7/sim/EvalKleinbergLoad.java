//
// Evaluate load on nodes imposed by greedy routing in a Kleinberg network
//
package sim;

import java.util.LinkedList;
import java.util.Random;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.Iterator;
import java.text.NumberFormat;

class EvalKleinbergLoad {

    final static int N = 1000;
    final static double D = 12;             // Average degree (incl nearest-neighbors)
    final static int HTL = 100;             // Let all succeed for now
    // final static int estsize = 30;       // Size of estimation table (naive)
    // final static int estsize = N/4;      // Size of estimation table (naive)
    final static int estsize = 100;

    final static boolean usedelays = true;
    final static boolean learn = true;
    final static boolean delays = true;
    final static boolean locals = false;

    final static boolean directed = false;
    final static boolean fixinc = false;    

    final static boolean print_unsuccessfuls = false;
    final static NumberFormat fmt = NumberFormat.getInstance();
    final static NumberFormat fmt4 = NumberFormat.getInstance();

    public static void main(String[] argv) {
	fmt.setMaximumFractionDigits(3);
	fmt4.setMaximumFractionDigits(4);

	System.err.println("Network size=" + N + ", Average degree="+(int)D);
	SimpleGraph g = new SimpleGraph(N, estsize, delays);
	g.CreateKleinbergGraph((int) (locals ? (D-2) : D),directed,locals,fixinc);

	g.usedelays();
	System.err.println("Insensitive to delays, no learning:");
	evalGreedy(g,HTL,100000,false);
	printEstimator(g.nodes.get(0),0);
	printEstimator(g.nodes.get(0),1);
	printEstimator(g.nodes.get(0),2);

	System.err.println("\nSensitive to delays, learning:");
	if(learn)
	    g.learning();
	
	evalGreedy(g,HTL,500000,usedelays);
	evalGreedy(g,HTL,500000,usedelays);
	evalGreedy(g,HTL,500000,usedelays);

	printEstimator(g.nodes.get(0),0);
	printEstimator(g.nodes.get(0),1);
	printEstimator(g.nodes.get(0),2);

	// printNeighborDistances(g.nodes[0],false);
	// g.printNodes(1); System.exit(0);
    }

    /*
     * Evaluate simple greedy routing on a network
     *
     * @param g: the graph for the network
     * @param htl: hops to live for routes
     * @nroutes: number of routes to evaluate (0=evaluate all routes)
     */
    public static void evalGreedy(SimpleGraph g,int htl,int nroutes,boolean usedelays) {
	// Random rand = new Random ((int) (System.currentTimeMillis() % 10000));
	int N = g.nodes.size();           // Override 
	Random rand = new Random(System.currentTimeMillis() % 10000);
	int succ=0,routes=0;
	double time_target=0,steps_success=0,steps_target=0,steps_failure=0,steps_tot=0;
	int nodes_active=0;
	int fails_deadend=0,fails_htlstop=0,fails_rejectloop=0;
	int netload=0;
	int[] used_lengths = new int[1+N/2];
	int[] edge_lengths = new int[1+N/2];
	int loops=0,deadends=0;

	System.err.println("Routing...");

	/*
	// Test a route	
	SimpleNode testsrc = g.nodes.get(0);
	SimpleNode testtarget = g.nodes.get(N/2);
	LinkedList<SimpleQuery> test = testsrc.route(new SimpleQuery(testsrc,null,testtarget,null,SimpleQuery.FORWARD,htl,false));
	System.err.println("\nRouting from " + testsrc.getLocation() + " to " + testtarget.getLocation());	
	for (SimpleQuery q : test) {
	    if(q.type == SimpleQuery.SUCCESS) {
		break;
	    }
	    int id = (int) (N*q.source.distanceTo(q.dest));
	    System.err.print(q.source.getLocation() + " => " + fmt.format(q.dest.getLocation()));
	    System.err.print("\t " + fmt.format(q.source.distanceTo(q.dest)) + "\t " + id);
	    System.err.print("\n");
	}
	printRoute(test);
	// printLoadsMatlabStyle(g);
	System.exit(0);	
	*/

	//for(int i=0; i<N; i++) {
	//for(int j=0; j<N; j++) {
	for(int k=0; k<1; k++) {
	    for(int kj=0; kj<nroutes; kj++) {	
		int i = rand.nextInt(g.nodes.size());
		int j = rand.nextInt(g.nodes.size());
		
		LinkedList<SimpleQuery> route;
		double time;
		int steps,load;

		// i -> j
		SimpleNode source = g.nodes.get(i);
		SimpleNode target = g.nodes.get(j);		
		route = source.route(new SimpleQuery(source,null,target,null,SimpleQuery.FORWARD,htl,false));
		// printRoute(route);
	       
		switch (route.getLast().type) {
		case SimpleQuery.SUCCESS:
		    succ++;
		    steps_success = steps_success + route.size();
		    break;
		    
		case SimpleQuery.DEADEND:
		    steps_failure = steps_failure + route.size();
		    fails_deadend++;
		    break;
		    
		case SimpleQuery.HTLSTOP:
		    steps_failure = steps_failure + route.size();
		    fails_htlstop++;
		    break;
		    
		case SimpleQuery.REJECTLOOP:
		    steps_failure = steps_failure + route.size();
		    fails_rejectloop++;
		    break;
		    
		default:
		    System.err.println("Route: returning with unknown state?");
		    System.exit(0);
		    break;
		}
		steps_tot = steps_tot + route.size();
		
		// Analyze query in detail
		boolean found = false;
		time = 0;
		steps = 0;
		load = 0;
		for(SimpleQuery q: route) {		

		    int id = (int) Math.round(N*q.source.distanceTo(q.dest));
		    used_lengths[id]++;

		    if(!found && (q.type == SimpleQuery.SUCCESS)) {
			found = true;
			time_target = time_target + time;
			steps_target = steps_target + steps;
		    } else {
			if(!q.internalQuery()) {
			    time = time + q.link.d;
			    steps++;
			}
		    }

		    // Count and forget
		    if(q.source.load != 0) {
			load = load + q.source.load;
			q.source.load = 0;
			nodes_active++;
		    }
		    
		    if(q.source.busy)
			q.source.busy = false;

		    if(q.type == SimpleQuery.DEADEND)
			deadends++;

		    if(q.type == SimpleQuery.REJECTLOOP)
			loops++;
		}
		netload = netload + load;
		routes++;

            }
	}

	double succrate = (double) succ/routes;
	int failures = routes - succ;
	double avgtime_forward = (double) time_target/succ;
	double avgsteps_forward = (double) steps_target/succ;
	double avglen_succ_rt = (double) steps_success/succ;
	double avglen_failed_rt = (double) ((routes-succ) == 0 ? 0 : steps_failure/(routes-succ));
	double avglen_all_rt = (double) steps_tot/routes;
	double avgload_nodes = (double) netload/nodes_active;

	/*
	  // Verbose output
	System.err.print("Result from " +routes+ " queries ");
	System.err.println("(HTL = " + htl + ")\n");	
	System.err.println("Success Rate = " + fmt.format(succrate) + " (" + fmt.format(failures) + " failures)");
	System.err.println("Avg Time, source to target\t" + fmt.format(avgtime_forward));
	System.err.println("Avg Steps, source to target\t" + fmt.format(avgsteps_forward));
	System.err.println("Avg Steps, successful roundtr\t" + fmt.format(avglen_succ_rt));
	System.err.println("AvgLen, failed roundtrips\t" + fmt.format(avglen_failed_rt));
	System.err.println("AvgLen, all roundtrips\t\t" + fmt.format(avglen_all_rt));
	*/

	/*
	// Count in-degrees (fixme: dont depend on position->index map)
	int[] indegree = new int[N];
	for(int i = 0; i<N; i++) {
	    for (SimplePeer peer: g.nodes.get(i).neighbors) {
		SimpleNode nout = peer.n;
		indegree[(int)Math.round(nout.getLocation()*N)]++;
	    }
	}
	*/

	// Collect lengths
	for(int i = 0; i<N; i++) {
	    for (SimplePeer peer : g.nodes.get(i).neighbors) {
		SimpleNode p = peer.n;
		edge_lengths[(int)Math.round(N*g.nodes.get(i).distanceTo(p))]++;
	    }
	}

	System.out.println(N + "\t" + fmt.format(succrate) + "\t" + fmt.format(avgtime_forward) + "\t" + fmt.format(avgsteps_forward) + "\t" + fmt.format(avglen_succ_rt) + "\t" + fmt.format(avglen_all_rt));

	/*
	  // Details for storage
	System.out.print("% N="+g.nodes.size()+",avg-degree="+D + ","+routes + " routes" + ",HTL="+htl);
	System.out.println(" " + (directed ? "directed" : "undirected") + " " + (locals ? "nearest-neighbor" : "no-nearest") + " " + (fixinc ? "fix-incoming" : "") + "alpha="+SimpleEstimator.alpha);
	System.out.println("% " + fmt.format(failures) + " failures," + deadends + " deadends," + fails_rejectloop + " fail-loops (" + loops +") " + fails_htlstop + " htlstops, " + "T_target=" + fmt.format(avgtime_forward) + ",L_target=" + fmt.format(avgsteps_forward) + " L_rtt=" + fmt.format(avglen_succ_rt) + " L_all=" + fmt.format(avglen_all_rt));
	*/
	// printLoadsMatlabStyle(g);
    }

    /*
     * Print load (to stdout) in convenient columns for each edge in a network
     *
     * @param g: the graph for the network
     */
    public static void printLoadsMatlabStyle(SimpleGraph g) {

	System.out.println("%src\tdst\tnewrcv\tnewsnt\tlinit\trinit\ttotin\ttotout\ttotload");
	for(SimpleNode n: g.nodes) {
	    int totload = 0;

	    for(SimplePeer peer: n.incoming) {
		SimpleNode nn = peer.n;
		printLinkLoadMatlabStyle(n,nn,g.nodes.size());

		if(n.traffic_incoming.get(nn).intValue() != nn.traffic_outgoing.get(n).intValue()) {
		    System.err.println("Asymmetric load: fixme :-)");
		    System.exit(-1);
		}
	    }

	    for(SimplePeer peer: n.neighbors) {
		SimpleNode nn = peer.n;
		if(!n.hasNeighbor(nn)) {
		    printLinkLoadMatlabStyle(n,nn,g.nodes.size());

		    if(n.traffic_incoming.get(nn).intValue() != nn.traffic_outgoing.get(n).intValue()) {
			System.err.println("Asymmetric load: fixme :-)");
			System.exit(-1);
		    }		    
		}
	    }
	}
    }

    /*
     * Print load (to stdout) for one edge
     *
     * @param n: the source endpoint
     * @param nn: the destination endpoint
     * @param nnodes: number of nodes in the network (mapping position -> int)
     */
    public static void printLinkLoadMatlabStyle(SimpleNode n,SimpleNode nn,int nnodes) {
	System.out.print(Math.round(nnodes*n.getLocation()) +"\t"+ Math.round(nnodes*nn.getLocation()));
	System.out.print("\t" + (n.new_received.containsKey(nn) ? n.new_received.get(nn).intValue() : 0));
	System.out.print("\t" + (nn.new_received.containsKey(n) ? nn.new_received.get(n).intValue() : 0));
	System.out.print("\t" + (n.init_local.containsKey(nn) ? n.init_local.get(nn).intValue()   : 0));
	System.out.print("\t" + (n.init_remote.containsKey(nn) ? n.init_remote.get(nn).intValue() : 0));
	System.out.print("\t" + (n.traffic_incoming.containsKey(nn) ? n.traffic_incoming.get(nn).intValue() : 0));
	System.out.print("\t" + (n.traffic_outgoing.containsKey(nn) ? n.traffic_outgoing.get(nn).intValue() : 0));
	System.out.println("");
    }

    /*
     * Print average distance (to stdout) for the connections of a node (was used for correlations)
     *
     * @param n: the node
     * @param boolean: if to only print incoming links
     */
    public static void printAverageNeighborDistance(SimpleNode n, boolean incoming) {
	System.out.print("\t[");
	double dists = 0.0;
	if(incoming) {
	    for(SimplePeer peer: n.incoming) {
		SimpleNode m = peer.n;
		dists = dists + n.distanceTo(m);		
	    }
	}
	System.out.print(fmt.format(dists/n.incoming.size()));
	System.out.print("]");	
    }

    /*
     * Print incoming load (to stdout) for a node
     *
     * @param n: the node
     */
    public static void printIncomingLoad(SimpleNode n) {
        System.out.print("\t[ ");
	int totload = 0;
	Set s = n.traffic_incoming.entrySet();
	Iterator i = s.iterator();

	while(i.hasNext()) {
	    Map.Entry me = (Map.Entry) i.next();
	    SimpleNode p = (SimpleNode) me.getKey();
	    Integer amount = (Integer) me.getValue();
	    totload = totload + amount.intValue();
	    System.out.print(amount.intValue() + ":" + fmt.format(p.getLocation()) + " ");
	}

        System.out.print("]");	
    }

    /*
     * Print distance information for each neighbor
     *
     * @n: node who has the neighbors
     * @incoming: only print for incoming edges
     */
    public static void printNeighborDistances(SimpleNode n,boolean incoming) {
	System.out.print("[ ");
	if(incoming) {
	    for(SimplePeer peer: n.incoming) {
		SimpleNode m = peer.n;
		System.out.print(fmt.format(n.distanceTo(m)) + " ");
	    }
	} else {
	    for(SimplePeer peer: n.neighbors) {
		SimpleNode m = peer.n;
		System.out.print(fmt.format(n.distanceTo(m)) + " ");
	    }
	}
	System.out.print("]");
    }

    public static void printNeighbors(SimpleNode n) {
	System.out.print("\t[ ");
	for(SimplePeer peer: n.neighbors) {
	    SimpleNode p = peer.n;
	    System.out.print(p.getLocation() + " ");
	}
	System.out.print("]");
    }

    public static void printNeighborLoad(SimpleNode n) {
	System.out.print("\t[ ");
	for(SimplePeer peer: n.neighbors) {
	    SimpleNode p = peer.n;
	    System.out.print(n.traffic_incoming.get(p).intValue() + " ");
	}
	System.out.print("]");
    }

    public static void printIncoming(SimpleNode n) {
	System.out.print("I[ ");
	for(SimplePeer peer: n.incoming) {	    
	    SimpleNode p = peer.n;
	    System.out.print(p.getLocation() + " ");
	}
	System.out.print("]");
    }

    /*
     * Print estimation information for one of the nodes
     *
     * @n: node who makes the estimation
     * @i: the neighbor to print for
     */
    public static void printEstimator(SimpleNode n,int i) {	
	SimpleEstimator est = n.estimators.get(n.neighbors.get(i).n);
	System.out.println("Node at " + n.getLocation() + " estimating through " + n.neighbors.get(i).n.getLocation());
	for(double d: est.length_times) {
	    System.out.println(fmt.format(d) + ",");
	}
	System.out.println("\n\n");
    }

    /*
     * Print information on how a route has proceeded
     *
     * @route: route to print about
     */
    public static void printRoute(LinkedList<SimpleQuery> route) {	
	boolean htlzero = false;
	System.err.println("Round-trip from " + route.getFirst().source.getLocation() + " to " + route.getFirst().target.getLocation() + ": ");
	for (SimpleQuery q: route) {
	    System.err.print(q.source.getLocation() + "->" + q.dest.getLocation() +"\t[");
	    switch(q.type) {
	    case SimpleQuery.SUCCESS:
		System.err.print("Success");
		break;
	    case SimpleQuery.DEADEND:
		System.err.print("Deadend");
		break;
	    case SimpleQuery.HTLSTOP:
		System.err.print("HTLstop");
		break;
	    case SimpleQuery.FORWARD:
		System.err.print("Forward");
		break;
	    case SimpleQuery.REJECTLOOP:
		System.err.print("RejLoop");
		break;
	    default:
		System.err.print("Unknown");
		break;
	    }

	    System.err.println("][htl="+q.htl+"][load="+q.source.load+"]"+ "\tdelay="+q.link.d);
	}
	System.err.println("");
    }
}
