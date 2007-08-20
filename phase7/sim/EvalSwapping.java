package sim;

import java.util.LinkedList;
import java.util.Random;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.Iterator;
import java.text.NumberFormat;

class EvalSwapping {

    final static int N = 10000;              // Number of nodes
    // final static double D = 12;             // Average degree (incl nearest-neighbors)
    final static double D = 20;

    static int swap_randwalk = 6;
    static int opennet_maxpeers = 15;

    static int rounds = 10000;
    static int eval_interval = 1000;
    static int randint_start = 0;
    static int randint_delta = 100;
    static int maxinterval = 0;

    // final static int HTL = (int) Math.pow(Math.log(N)/Math.log(2),2.0);
    final static int HTL = 100;             // Let all succeed for now    
    final static boolean locals = false;    // Nearest-neighbor contacts by default
    final static boolean directed = false;  // Use directed edges?
    final static boolean fixinc = false;    // Fix incoming degree (with directed edges)
    final static boolean delays = false;

    final static boolean print_unsuccessfuls = false;
    final static NumberFormat fmt = NumberFormat.getInstance();
    final static NumberFormat fmt4 = NumberFormat.getInstance();
    
    public static void main(String[] argv) {
	fmt.setMaximumFractionDigits(3);
        fmt4.setMaximumFractionDigits(4);
	Random rand = new Random(System.currentTimeMillis() % 10000);

        System.err.println("Network size=" + N + ", Average degree="+(int)D);

	for (int randomize_interval = randint_start; randomize_interval<maxinterval+1; randomize_interval=randomize_interval+randint_delta) {
	    SimpleGraph g = new SimpleGraph(N, 0, delays);
	    g.CreateKleinbergGraph((int) (locals ? (D-2) : D),directed,locals,fixinc);

	    int i = 0;
	    System.out.println("%N="+N+",interval="+randomize_interval+",avgdeg="+D + ",HTL="+HTL);
	    System.out.println("%initial performance");
	    System.out.print(i + "\t" + randomize_interval + "\t");
	    EvalKleinbergLoad.evalGreedy(g,HTL,100000,false);
	    
	    g.randLocations();
	    // g.enableOpennet(opennet_maxpeers);
	    if (randomize_interval>0) {
		for (SimpleNode n: g.nodes) {
		    n.rand_interval = rand.nextInt(randomize_interval);
		}
	    }
	    // g.shuffleLocations();
	    
	    System.out.println("%shuffled performance");
	    System.out.print(i + "\t" + randomize_interval + "\t");
	    EvalKleinbergLoad.evalGreedy(g,HTL,100000,false);
	    System.err.println("Swapping...");
	    System.out.println("%randomized performance " + 100*N);
	    for(i=0; i<(rounds*N)+1; i++) {
		// g.tryswap(0,randomize_interval);
		g.tryswap(swap_randwalk,randomize_interval);
		
		if (((i % (eval_interval*N))==0) || ((i < eval_interval) && ((i % (N*eval_interval/4))==0))) {
		    System.out.print(i + "\t" + randomize_interval + "\t");
		    EvalKleinbergLoad.evalGreedy(g,HTL,1000,false);
		    // EvalKleinbergLoad.evalGreedy(g,HTL,100000,false);
		}		
	    }
	}
    }

}
