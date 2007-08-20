//
// Evaluate impact of churn on an ideal network; especially how churn affects
// the clustering of positions.
// Input: parameters below
// Output to stdout:
// 1) initial positions
// 2) positions after churn+swapping
// 3) positions taken by nodes that joined the network
// 

package sim;

import java.util.LinkedList;
import java.util.Random;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.Iterator;
import java.text.NumberFormat;

class EvalChurn {

    // Network parameters
    final static int N = 1000;
    final static int D = 10;                 // Darknet connections
    static boolean swap_uniform = false;            // Swap uniformly or with random walk approximation
    static int swap_randwalk = 6;
    static int opennet_maxpeers = 15;        // Node gets this number if opennet enabled
    final static int HTL = 100;
    // final static int HTL = (int) Math.pow(Math.log(N)/Math.log(2),2.0);    // Oskars paper
    final static boolean locals = false;    // Nearest-neighbor contacts
    final static boolean directed = false;  // Use directed edges
    final static boolean fixinc = false;    // Fix incoming degree (with directed edges)
    final static boolean delays = false;    // Vary delays on edges or not

    // Evaluation intensity
    static int rounds = 1000;
    static int eval_interval = 100;
    static int randint_start = 0;
    static int randint_delta = 100;
    static int maxinterval = 0;
    static int routes_to_swap_fraction = 3;

    final static boolean print_unsuccessfuls = false;
    final static NumberFormat fmt = NumberFormat.getInstance();
    final static NumberFormat fmt4 = NumberFormat.getInstance();
    
    public static void main(String[] argv) {
	fmt.setMaximumFractionDigits(3);
        fmt4.setMaximumFractionDigits(4);
	Random rand = new Random(System.currentTimeMillis() % 10000);	      

	/*
	// To study position randomization
	if (randomize_interval>0) {
	    for (SimpleNode n: g.nodes) {
		n.rand_interval = rand.nextInt(randomize_interval);
	    }
	}
	*/

	/*
	// To evaluate adding nodes afterwards
	for (i=0; i<1000; i++) {
	    SimpleNode tmp1 = g.insertNode((int) (locals ? (D-2) : D),directed,locals,fixinc);
	}

	EvalKleinbergLoad.evalGreedy(g,HTL,200000,false);
	System.exit(0);
	*/

	int i = 0;
        System.err.println("Network size=" + N + ", Average degree="+(int)D);
	SimpleGraph g = new SimpleGraph(N, 0, delays);
	g.CreateKleinbergGraph((int) (locals ? (D-2) : D),directed,locals,fixinc);

	System.out.println("%N="+N+",interval="+",avgdeg="+D + ",HTL="+HTL);
	System.out.println("%initial performance");
	System.out.print(0 + "\t");
	EvalKleinbergLoad.evalGreedy(g,HTL,100000,false);

	// If to shuffle the initial (uniformly spread) positions, or to completely randomize
	g.shuffleLocations();
	// g.randLocations();
	
	System.out.println("%shuffled performance");
	System.out.print(0 + "\t");
	EvalKleinbergLoad.evalGreedy(g,HTL,100000,false);	

	System.err.println("Swapping...");
	for(int j=0;j<N*rounds;j++) {
	    g.tryswap(swap_randwalk,0);
	}

	System.out.println("%randomized performance ");
	EvalKleinbergLoad.evalGreedy(g,HTL,100000,false);
	g.printLocations(1);
	
	int join_times = 10000;
	int join_swaps = 10*N;
	double[] added_locations = new double[join_times*5];
	int added=0;

	// Add 5 nodes join times, let each node swap on average join_swaps times, remove them
	System.err.println("Joining/Swapping...");
	for (i=0; i<join_times; i++) {
	    SimpleNode tmp1 = g.insertNode((int) (locals ? (D-2) : D),directed,locals,fixinc);
	    SimpleNode tmp2 = g.insertNode((int) (locals ? (D-2) : D),directed,locals,fixinc);
	    SimpleNode tmp3 = g.insertNode((int) (locals ? (D-2) : D),directed,locals,fixinc);
	    SimpleNode tmp4 = g.insertNode((int) (locals ? (D-2) : D),directed,locals,fixinc);
	    SimpleNode tmp5 = g.insertNode((int) (locals ? (D-2) : D),directed,locals,fixinc);
	    tmp1.setLocation(rand.nextDouble());
	    tmp2.setLocation(rand.nextDouble());
	    tmp3.setLocation(rand.nextDouble());
	    tmp4.setLocation(rand.nextDouble());
	    tmp5.setLocation(rand.nextDouble());

	    added_locations[added++] = tmp1.getLocation();
	    added_locations[added++] = tmp2.getLocation();
	    added_locations[added++] = tmp3.getLocation();
	    added_locations[added++] = tmp4.getLocation();
	    added_locations[added++] = tmp5.getLocation();

	    for (int j=0; j<join_swaps; j++) {
		if (swap_uniform) {
		    g.tryswap(0,0);
		} else {
		    g.tryswap(swap_randwalk,0);
		}
	    }
	    
	    // System.err.println("Result: network size is " + g.nodes.size());
	    // EvalKleinbergLoad.evalGreedy(g,HTL,20000,false);
	    g.removeNode(tmp1);
	    g.removeNode(tmp2);
	    g.removeNode(tmp3);
	    g.removeNode(tmp4);
	    g.removeNode(tmp5);
	}

	System.err.println("Result: network size is " + g.nodes.size());
	// EvalKleinbergLoad.evalGreedy(g,HTL,100000,false);
	System.out.println("after");
	g.printLocations(1);

	System.out.println("added");
	for (double d: added_locations) {
	    System.out.println(d);
	}
	
	/*
	  // Using opennet instead (also)
	g.enableOpennet(opennet_maxpeers);
	for(i=0; i<(rounds*N)+1; i++) {
	    g.tryswap(swap_randwalk,randomize_interval);
	    g.randomroute(HTL);
	    
	    if ((i % (eval_interval*N))==0) {
		System.err.println("Evaluating result: ");
		g.disableOpennet();
		EvalKleinbergLoad.evalGreedy(g,HTL,100000,false);
		g.enableOpennet(opennet_maxpeers);
	    }
	    
	}
	
	for(i=0; i<(rounds*N)+1; i++) {
	    // g.tryswap(0,randomize_interval);	      
	    g.tryswap(swap_randwalk,randomize_interval);
	    
	    if (((i % (routes_to_swap_fraction*eval_interval*N))==0) || ((i < eval_interval) && ((i % (N*eval_interval/4))==0))) {
		System.out.print(i + "\t" + randomize_interval + "\t");
		EvalKleinbergLoad.evalGreedy(g,HTL,10000,false);
	    }		
	}
	*/
    }
}
