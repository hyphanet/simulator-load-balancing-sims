//
// Evaluate load by greedy routing on nodes in a Kleinberg network
//
package sim;

import java.util.LinkedList;
import java.util.Random;
import java.text.NumberFormat;

class EvalKleinbergLoad {

    final static int N = 500;
    final static double D = 5;           // Number of shortcuts
    //final static int routes = 100;
    final static int routes = N*(N-1);   // Between all pairs, both directions
    final static int HTL = 50;          // Let all succeed for now
    final static boolean print_unsuccessfuls = false;
    final static NumberFormat fmt = NumberFormat.getInstance();

    public static void main(String[] argv) {
	fmt.setMaximumFractionDigits(2);

	SimpleGraph g = new SimpleGraph(N);
	System.err.println("Network size=" + N + ", Average degree="+(int)D);

	g.CreateKleinbergGraph((int)D,true,true);      // Directed and with local edges
	evalDirected(g,HTL);
    }

    public static void evalDirected(SimpleGraph g,int htl) {
	Random rand = new Random ((int) (System.currentTimeMillis() % 10000));
	int succ=0;
	int steps_target=0,steps_success=0,steps_failure=0,steps_tot=0;
	int nodes_active=0;
	int fails_deadend=0,fails_htlstop=0,fails_rejectloop=0;
	int netload=0;

	System.err.print("Result from " +routes+ " queries ");
	System.err.println("(HTL = " + htl + ")\n");

	for(int i=0; i<N; i++) {
	    for(int j=0; j<i; j++) {

		SimpleNode src,dst;
		LinkedList<SimpleQuery> route;
		boolean found;
		int len, load;

		//
		// i -> j
		//

		src = g.nodes[i];
		dst = g.nodes[j];
		
		src.initiated++;
		dst.initiated++;

		route = src.route(new SimpleQuery(src,dst,SimpleQuery.FORWARD,htl));
		
		switch (route.getLast().type) {
		case SimpleQuery.SUCCESS:
		    succ++;
		    steps_success = steps_success + route.size()-1;
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
		    steps_failure = steps_failure + route.size()-1;
		    if(print_unsuccessfuls) {
			System.err.println("Unsuccessful route: ");
			printRoute(route);
		    }
		    break;
		}
		steps_tot = steps_tot + route.size()-1;
		
		/* Free nodes for queries, count steps to target */
		found = false;
		len = 0;
		load = 0;
		for(SimpleQuery q: route) {		
		    if(!found && (q.type == SimpleQuery.SUCCESS)) {
			found = true;
			steps_target = steps_target + len;
		    } else {
			len++;
		    }
		    
		    if(q.peer.load != 0) {
			load = load + q.peer.load;    // Forget load
			q.peer.load = 0;
			nodes_active++;
		    }
		    
		    if(q.peer.busy != 0) {
			q.peer.busy = 0;
		    }
		}
		netload = netload + load;


		//
		// j -> i
		//

		src = g.nodes[j];
		dst = g.nodes[i];
		
		src.initiated++;
		dst.initiated++;

		route = src.route(new SimpleQuery(src,dst,SimpleQuery.FORWARD,htl));
		
		switch (route.getLast().type) {
		case SimpleQuery.SUCCESS:
		    succ++;
		    steps_success = steps_success + route.size()-1;
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
		    steps_failure = steps_failure + route.size()-1;
		    if(print_unsuccessfuls) {
			System.err.println("Unsuccessful route: ");
			printRoute(route);
		    }
		    break;
		}
		steps_tot = steps_tot + route.size()-1;
		
		/* Free nodes for queries, count steps to target */
		found = false;
		len = 0;
		load = 0;
		for(SimpleQuery q: route) {		
		    if(!found && (q.type == SimpleQuery.SUCCESS)) {
			found = true;
			steps_target = steps_target + len;
		    } else {
			len++;
		    }
		    
		    if(q.peer.load != 0) {
			load = load + q.peer.load;    // Forget load
			q.peer.load = 0;
			nodes_active++;
		    }
		    
		    if(q.peer.busy != 0) {
			q.peer.busy = 0;
		    }
		}
		netload = netload + load;

	    }
	    
	}

	double succrate = (double) succ/routes;
	int failures = routes - succ;
	double avglen_forward = (double) steps_target/succ;
	double avglen_succ_rt = (double) steps_success/succ;
	double avglen_failed_rt = (double) steps_failure/(routes-succ);
	double avglen_all_rt = (double) steps_tot/routes;
	double avgload_nodes = (double) netload/nodes_active;
	
	System.err.println("Success Rate = " + fmt.format(succrate) + " (" + fmt.format(failures) + " failures)");
	System.err.println("AvgLen, source to target\t" + fmt.format(avglen_forward));
	System.err.println("AvgLen, successful roundtrips\t" + fmt.format(avglen_succ_rt));
	System.err.println("AvgLen, failed roundtrips\t" + fmt.format(avglen_failed_rt));
	System.err.println("AvgLen, all roundtrips\t\t" + fmt.format(avglen_all_rt));
	//System.err.print("Failure reasons: ");
	//System.err.println("Average Load (messages per node and query) = " + fmt.format(avgload_nodes));


	int[] indegree = new int[N];
	for(int i = 0; i<N; i++) {
	    for (SimpleNode nout: g.nodes[i].neighbors) {
		indegree[(int)Math.round(nout.getLocation()*N)]++;
	    }
	}

	System.err.println("node loads: ");
	System.out.println("% N="+N+",Shortcuts="+D);
	System.out.println("% load\tindeg\toutdeg");
	for(int i = 0; i<N; i++) {
	    System.out.print(g.nodes[i].loadmem);
	    System.out.print("\t" + indegree[i]);
	    System.out.print("\t" + g.nodes[i].getDegree());
	    //g.nodes[i].printNeighbors();
	    //System.out.print("\t\tI:" + indegree[i] + "\t");
	    //g.nodes[i].printIncoming();
	    System.out.println("");
	}

    }

    public static void evalUndirected(SimpleGraph g,int htl) {	
    }

    public static void printRoute(LinkedList<SimpleQuery> route) {
	
	boolean htlzero = false;
	System.err.println("path from " + route.getFirst().peer.getLocation() + " to " + route.getFirst().target.getLocation() + ": ");
	for (SimpleQuery q: route) {
	    System.err.print(q.peer.getLocation() +"\t[");
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
		System.err.print("");
		break;
	    }

	    // Some sugar to map SimpleQuery.htl->SimpleNode.htl
	    System.err.println("][htl=" + (!htlzero ? (q.htl+1) : 0) +"][load="+q.peer.load+"]");
	    if(!htlzero && (q.htl==0))
		htlzero = true;
	    
	}
    }
}
