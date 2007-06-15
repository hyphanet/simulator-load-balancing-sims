/*
 * Graph of SimpleNodes 
 */

package sim;

import java.util.Random;

class SimpleGraph {

    SimpleNode[] nodes;
    private Random rand;

    public SimpleGraph(int n) {
	nodes = new SimpleNode[n];
	for (int i=0; i<n; i++) {
	    nodes[i] = new SimpleNode((double)i/n);
	}
	rand = new Random((int) (System.currentTimeMillis() % 10000));
    }

    /*
     * Generating a Kleinberg small-world graph with shortcuts proportional to 1/d
     *
     * @avgdeg: average degree of nodes
     * @directed: if to have directed edges
     * @local: if to have local edges (to nearest-neighbors in the lattice)
     */

    public void CreateKleinbergGraph(double avgdeg, boolean directed, boolean local) {

	double degree;
	if(directed) {
	    degree = avgdeg;
	} else {
	    degree = avgdeg/2;
	}

	if(local) {
	    for(int i=0; i<nodes.length; i++) {
		SimpleNode src = nodes[i];
		SimpleNode dst = nodes[(i+1)%nodes.length];
		src.connect(dst);
		dst.connect(src);
	    }
	}

	for(int i=0; i<nodes.length; i++) {
	    SimpleNode src = nodes[i];

	    for(int j=0; j<degree; j++) {

		boolean found = false;
		while(!found) {

		    // assuming nodes are equally spaced out in [0,1] we can use integers as offsets
		    // fixme: option for sampling directly in [0,1]
		    double r = rand.nextFloat();
                    int d = (int) Math.floor (Math.exp (r*Math.log (nodes.length/2.0)));
		    int destpos;

		    if (rand.nextFloat()<0.5) {
			destpos = i - d;
		    } else {
			destpos = i + d;
		    }

		    if (destpos>=nodes.length) {
			destpos = destpos - nodes.length;
		    } else if (destpos<0) {
			destpos = destpos + nodes.length;
		    }

		    SimpleNode dst = nodes[destpos];

		    if (directed) {
			if (!src.hasNeighbor(dst)) {
			    found = true;
			    src.connect(dst);
			}
		    } else {
			if (!src.hasNeighbor(dst) && !dst.hasNeighbor(src)) {
			    found = true;
			    src.connect(dst);
			    dst.connect(src);
			}
		    }
		    
		    
		}
	    }

	}
    }


}
