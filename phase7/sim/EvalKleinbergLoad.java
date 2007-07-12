//
// Evaluate load by greedy routing on nodes in a Kleinberg network
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
    final static double D = 10;            // Average degree (incl nearest-neighbors)
    final static int HTL = N/2;            // Let all succeed for now
    final static boolean directed = false;
    final static boolean locals = false;
    final static boolean fixinc = false;

    final static boolean print_unsuccessfuls = false;
    final static NumberFormat fmt = NumberFormat.getInstance();
    final static NumberFormat fmt4 = NumberFormat.getInstance();

    public static void main(String[] argv) {
	fmt.setMaximumFractionDigits(3);
	fmt4.setMaximumFractionDigits(4);

	SimpleGraph g = new SimpleGraph(N);
	System.err.println("Network size=" + N + ", Average degree="+(int)D);

	//g.CreateKleinbergGraph((int)D,true,true,true); //directed,local,fixincoming
	//g.CreateKleinbergGraph((int)D,false,true,false); //undirected,local,outgoing
	//g.CreateKleinbergGraph((int)D,false,false,false); //undirected,nolocal,outgoing
	g.CreateKleinbergGraph((int) (locals ? (D-2) : D),directed,locals,fixinc);
	evalGreedy(g,HTL);
    }

    /*
     * Evaluate simple greedy routing on a network
     *
     * @param g: the graph for the network
     * @param htl: hops to live for routes
     */
    public static void evalGreedy(SimpleGraph g,int htl) {
	Random rand = new Random ((int) (System.currentTimeMillis() % 10000));
	int succ=0;
	int routes=0;
	int steps_target=0,steps_success=0,steps_failure=0,steps_tot=0;
	int nodes_active=0;
	int fails_deadend=0,fails_htlstop=0,fails_rejectloop=0;
	int netload=0;
	int[] used_lengths = new int[N/2];
	int[] edge_lengths = new int[N/2];
	int n_edges=0, n_usages=0;
	int loops=0,deadends=0;

	System.err.println("Routing...");

	/*
	// Test a route	
	SimpleNode testsrc = g.nodes[0];
	SimpleNode testtarget = g.nodes[N/2];
	LinkedList<SimpleQuery> test = testsrc.route(new SimpleQuery(testsrc,null,testtarget,SimpleQuery.FORWARD,htl));
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

	for(int i=0; i<N; i++) {
	    for(int j=0; j<i; j++) {

		SimpleNode source,target;
		LinkedList<SimpleQuery> route;
		boolean found;
		int len, load;

		//
		// i -> j
		//

		source = g.nodes[i];
		target = g.nodes[j];
		
		route = source.route(new SimpleQuery(source,null,target,SimpleQuery.FORWARD,htl));
		
		switch (route.getLast().type) {
		case SimpleQuery.SUCCESS:
		    succ++;
		    steps_success = steps_success + route.size();
		    break;
		    
		case SimpleQuery.DEADEND:
		    fails_deadend++;
		    break;
		    
		case SimpleQuery.HTLSTOP:
		    fails_htlstop++;
		    break;
		    
		case SimpleQuery.REJECTLOOP:
		    fails_rejectloop++;
		    break;
		    
		default:
		    steps_failure = steps_failure + route.size();
		    if(print_unsuccessfuls) {
			System.err.println("Unsuccessful route: ");
			printRoute(route);
		    }
		    break;
		}
		steps_tot = steps_tot + route.size();
		
		// Free nodes for queries, count steps and lengths
		found = false;
		len = 0;
		load = 0;
		for(SimpleQuery q: route) {		

		    int id = (int) Math.round(N*q.source.distanceTo(q.dest));
		    used_lengths[id]++;
		    n_usages++;

		    if(!found && (q.type == SimpleQuery.SUCCESS)) {
			found = true;
			steps_target = steps_target + len;
		    } else {
			len++;
		    }
		    
		    if(q.source.load != 0) {
			load = load + q.source.load;    // Forget load
			q.source.load = 0;
			nodes_active++;
		    }
		    
		    if(q.source.busy) {
			q.source.busy = false;
		    }

		    if(q.type == SimpleQuery.DEADEND)
			deadends++;
		    if(q.type == SimpleQuery.REJECTLOOP)
			loops++;
		}
		netload = netload + load;
		routes++;

		//
		// j -> i
		//

		source = g.nodes[j];
		target = g.nodes[i];
		
		route = source.route(new SimpleQuery(source,null,target,SimpleQuery.FORWARD,htl));
		
		switch (route.getLast().type) {
		case SimpleQuery.SUCCESS:
		    succ++;
		    steps_success = steps_success + route.size();
		    break;
		    
		case SimpleQuery.DEADEND:
		    fails_deadend++;
		    break;
		    
		case SimpleQuery.HTLSTOP:
		    fails_htlstop++;
		    break;
		    
		case SimpleQuery.REJECTLOOP:
		    fails_rejectloop++;
		    break;
		    
		default:
		    steps_failure = steps_failure + route.size();
		    if(print_unsuccessfuls) {
			System.err.println("Unsuccessful route: ");
			printRoute(route);
		    }
		    break;
		}
		steps_tot = steps_tot + route.size();
	      
		// Free nodes for queries, count steps and lengths
		found = false;
		len = 0;
		load = 0;
		for(SimpleQuery q: route) {		

		    int id = (int) Math.round(N*q.source.distanceTo(q.dest));
		    used_lengths[id]++;
		    n_usages++;

		    if(!found && (q.type == SimpleQuery.SUCCESS)) {
			found = true;
			steps_target = steps_target + len;
		    } else {
			len++;
		    }
		    
		    if(q.source.load != 0) {
			load = load + q.source.load;    // Forget load
			q.source.load = 0;
			nodes_active++;
		    }
		    
		    if(q.source.busy) {
			q.source.busy = false;
		    }

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
	double avglen_forward = (double) steps_target/succ;
	double avglen_succ_rt = (double) steps_success/succ;
	double avglen_failed_rt = (double) ((routes-succ) == 0 ? 0 : steps_failure/(routes-succ));
	double avglen_all_rt = (double) steps_tot/routes;
	double avgload_nodes = (double) netload/nodes_active;

	System.err.print("Result from " +routes+ " queries ");
	System.err.println("(HTL = " + htl + ")\n");
	
	System.err.println("Success Rate = " + fmt.format(succrate) + " (" + fmt.format(failures) + " failures)");
	System.err.println("AvgLen, source to target\t" + fmt.format(avglen_forward));
	System.err.println("AvgLen, successful roundtrips\t" + fmt.format(avglen_succ_rt));
	System.err.println("AvgLen, failed roundtrips\t" + fmt.format(avglen_failed_rt));
	System.err.println("AvgLen, all roundtrips\t\t" + fmt.format(avglen_all_rt));

	// Count degrees
	int[] indegree = new int[N];
	for(int i = 0; i<N; i++) {
	    for (SimpleNode nout: g.nodes[i].neighbors) {
		indegree[(int)Math.round(nout.getLocation()*N)]++;
	    }
	}

	// Collect lengths
	for(int i = 0; i<N; i++) {
	    for (SimpleNode p : g.nodes[i].neighbors) {
		edge_lengths[(int)Math.round(N*g.nodes[i].distanceTo(p))]++;
		n_edges++;                    // directed
	    }
	}

	System.out.println("% N="+N+",avg-degree="+D);
	System.out.println("% " + (directed ? "directed" : "undirected") + " " + (locals ? "nearest-neighbor" : "no-nearest") + " " + (fixinc ? "fix-incoming" : ""));
	System.out.println("% " + fmt.format(failures) + " failures," + deadends + " deadends," + loops + " loops, " + " L_target=" + fmt.format(avglen_forward) + " L_rtt=" + fmt.format(avglen_succ_rt) + " L_all=" + fmt.format(avglen_all_rt));
	printLoadsMatlabStyle(g);
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

	    for(SimpleNode nn: n.incoming) {
		printLinkLoadMatlabStyle(n,nn,g.nodes.length);

		if(n.traffic_incoming.get(nn).intValue() != nn.traffic_outgoing.get(n).intValue()) {
		    System.err.println("Asymmetric load: fixme :-)");
		    System.exit(-1);
		}
	    }

	    for(SimpleNode nn: n.neighbors) {
		if(!n.incoming.contains(nn)) {
		    printLinkLoadMatlabStyle(n,nn,g.nodes.length);

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
	    for(SimpleNode m: n.incoming) {
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

    public static void printNeighborDistances(SimpleNode n,boolean incoming) {
	System.out.print("[ ");
	if(incoming) {
	    for(SimpleNode m: n.incoming) {
		System.out.print(fmt.format(n.distanceTo(m)) + " ");
	    }
	}
	System.out.print("]");
    }

    public static void printNeighbors(SimpleNode n) {
	System.out.print("\t[ ");
	for(SimpleNode p: n.neighbors) {
	    System.out.print(p.getLocation() + " ");
	}
	System.out.print("]");
    }

    public static void printNeighborLoad(SimpleNode n) {
	System.out.print("\t[ ");
	for(SimpleNode p: n.neighbors) {
	    System.out.print(n.traffic_incoming.get(p).intValue() + " ");
	}
	System.out.print("]");
    }

    public static void printIncoming(SimpleNode n) {
	System.out.print("I[ ");
	for(SimpleNode p: n.incoming) {	    
	    System.out.print(p.getLocation() + " ");
	}
	System.out.print("]");
    }

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

	    System.err.println("][htl="+q.htl+"][load="+q.source.load+"]");
	}
	System.err.println("");
    }
}
