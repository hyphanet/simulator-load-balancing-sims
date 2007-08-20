//
// The graph holding the simulation network topology
// Takes care of routing, position randomization, also some node configuration
//

package sim;
import java.util.Random;
import java.util.Vector;
import java.util.LinkedList;

class SimpleGraph {

    // SimpleNode[] nodes;
    Vector<SimpleNode> nodes;           // Growable, but array should be more efficient
    private static Random rand = new Random(System.currentTimeMillis() % 10000);
    private boolean delays = false;     // If to vary delays by default
    
    /*
     * @n: number of nodes
     * @estsize: size of estimation table
     * @delays: if to sample delays between nodes, or to use unity
     */
    public SimpleGraph(int n, int estsize, boolean delays) {
	this.delays = delays;
	// nodes = new SimpleNode[n];
	nodes = new Vector<SimpleNode>();
	for (int i=0; i<n; i++) {
	    double location = (double)i/n;
	    nodes.add(new SimpleNode(location, estsize));
	}
    }

    /*
     * To enable opennet for all nodes.
     * @opennet_maxpeers: max peers that a node wants to take on opennet
     */
    public void enableOpennet(int opennet_maxpeers) {
	for (SimpleNode n: nodes) {
	    n.enableOpennet(opennet_maxpeers);
	}
    }

    /*
     * To disable opennet for all nodes.
     */
    public void disableOpennet() {
	for (SimpleNode n: nodes) {
	    n.disableOpennet();
	}
    }

    /*
     * Make a route in the network (possibly affecting opennet if enabled)
     * Peers chosen uniformly at random
     *
     * @htl: Hops to Live
     */
    public void randomroute(int htl) {
	SimpleNode src = nodes.get(rand.nextInt(nodes.size()));
	SimpleNode dst = nodes.get(rand.nextInt(nodes.size()));
	LinkedList<SimpleQuery> route = src.route(new SimpleQuery(src,null,dst,null,SimpleQuery.FORWARD,htl,false));
	unlock(route);
    }

    /*
     * Unlock simple trackkeeping of route participation
     *
     * @route: The route with all parcitipants
     */

    public void unlock(LinkedList<SimpleQuery> route) {
	for (SimpleQuery rq: route) {
	    rq.source.busy = false;
	}
    }

    /*
     * Randomly assign new locations in [0,1) to nodes
     */
    public void randLocations() {
	for (SimpleNode n: nodes) {
	    n.setLocation(rand.nextDouble());
	}	
    }

    /*
     * Randomly shuffle the existing positions
     */
    public void shuffleLocations() {
	for(int j=0; j<nodes.size(); j++) {
            int nn = rand.nextInt(nodes.size());
            SimpleNode nj = nodes.get(j);
            SimpleNode swapto = nodes.get(nn);

            double tmp = nj.getLocation();
	    nj.setLocation(swapto.getLocation());
            swapto.setLocation(tmp);

            nodes.set(j,swapto);
            nodes.set(nn,nj);
        }
    }

    /*
     * Generate a delay of 0.1 + a harmonic (1/x) distribution and with mean 1.
     *
     * fixme: try a Gaussian with mean 1? (restricted downwards by a min value)
     */
    public static double sampleDelay() {
	double delaymin = 0.1;
	// return (delaymin + 0.9*0.1*Math.pow(37.5,rand.nextDouble()));
	return (delaymin + 0.9*0.0001*Math.pow(119000,rand.nextDouble()));
	//return ((rand.nextDouble()<0.5)?0.1:1.9);
	// return ((rand.nextDouble()<0.5)?0.5:1.5);
    }

    /*
     * Patch a node into Kleinbergs original model
     * Warning: this does NOT give a Kleinberg model, dont use this for growing networks
     * Used to evaluate quick churn into the darknet model.
     *
     * @avgdeg: resulting average degree of the nodes
     * @directed: if to use directed edges
     * @local: if to add nearest-neighbor connections
     * @inconst: if to keep the *indegree* constant (fixme)
     *
     */
    public SimpleNode insertNode(double avgdeg, boolean directed, boolean local, boolean inconst) {

	int i = rand.nextInt(nodes.size());
	SimpleNode newnode = new SimpleNode(rand.nextDouble(),0);
	nodes.insertElementAt(newnode,i);

        double degree,delay;
        if(directed) {
            degree = avgdeg;
        } else {
            degree = avgdeg/2;
        }

	SimpleNode src = nodes.get(i);
	if (local) {
	    SimpleNode dst;
	    SimpleLink link;

	    if(delays) 
		delay = sampleDelay();
	    else
		delay = 1;
	    dst = nodes.get((i+1)%nodes.size());
	    link = new SimpleLink(src,dst,delay);
	    src.connect(dst,link,SimplePeer.t_DARKNET);
	    dst.connect(src,link,SimplePeer.t_DARKNET);

	    if(delays) 
		delay = sampleDelay();
	    else
		delay = 1;
	    dst = nodes.get((i-1)%nodes.size());
	    link = new SimpleLink(src,dst,delay);
	    src.connect(dst,link,SimplePeer.t_DARKNET);
	    dst.connect(src,link,SimplePeer.t_DARKNET);
	}

	for(int j=0; j<degree; j++) {
	    boolean found = false;
	    while(!found) {
		double r = rand.nextFloat();
		int d = (int) Math.floor (Math.exp (r*Math.log (nodes.size()/2.0)));
		int destpos;
		
		if (rand.nextFloat()<0.5) {
		    destpos = i - d;
		} else {
		    destpos = i + d;
		}
		
		if (destpos>=nodes.size()) {
		    destpos = destpos - nodes.size();
		} else if (destpos<0) {
		    destpos = destpos + nodes.size();
		}
		
		SimpleNode dst = nodes.get(destpos);
		
		if(delays)
		    delay = sampleDelay();
		else
		    delay = 1;
		
		if (directed) {
		    if (inconst) {
			if (!dst.hasNeighbor(src)) {
			    found = true;
			    SimpleLink sl = new SimpleLink(src,dst,delay);
			    dst.connect(src,sl,SimplePeer.t_DARKNET);
			}
		    } else {
			if (!src.hasNeighbor(dst)) {
			    found = true;
			    SimpleLink sl = new SimpleLink(dst,src,delay);
			    src.connect(dst,sl,SimplePeer.t_DARKNET);
			}
		    }
		} else {
		    if (!src.hasNeighbor(dst) && !dst.hasNeighbor(src)) {
			found = true;
			SimpleLink sl = new SimpleLink(src,dst,delay);
			src.connect(dst,sl,SimplePeer.t_DARKNET);
			dst.connect(src,sl,SimplePeer.t_DARKNET);
		    }
		}		    		    
	    }
	}

	return newnode;
    }

    /*
     * Remove a node from the graph
     *
     * fixme: also remove load stats/counters
     * @n: node to remove
     */
    public void removeNode (SimpleNode n) {

	Vector<SimpleNode> toDisconnect = new Vector<SimpleNode>();

	for (SimplePeer peer: n.neighbors) {
	    peer.n.disconnect(n);
	    toDisconnect.add(peer.n);
	}

	for (SimpleNode disconnecter: toDisconnect) {
	    n.disconnect(disconnecter);
	}

	nodes.removeElement(n);
    }

    /*
     * Generating a Kleinberg small-world graph with shortcuts proportional to 1/d
     *
     * @avgdeg: average degree of nodes
     * @directed: if to have directed edges
     * @local: if to have local edges (to nearest-neighbors in the lattice)
     * @inconst: if to have a constant number of indegree edges (when using directed edges),
     *           otherwise use constant outdegree (default)
     */
    public void CreateKleinbergGraph(double avgdeg, boolean directed, boolean local, boolean inconst) {

	double degree,delay;
	if(directed) {
	    degree = avgdeg;
	} else {
	    degree = avgdeg/2;
	}

	if(local) {
	    for(int i=0; i<nodes.size(); i++) {
		if(delays) 
		    delay = sampleDelay();
		else
		    delay = 1;
		
		SimpleNode src = nodes.get(i);
		SimpleNode dst = nodes.get((i+1)%nodes.size());
		SimpleLink link = new SimpleLink(src,dst,delay);
		src.connect(dst,link,SimplePeer.t_DARKNET);
		dst.connect(src,link,SimplePeer.t_DARKNET);
	    }
	}

	System.err.println("Giving each node " + degree + " shortcuts" + (local ? " and nearest-neighbor connections " : "") + " (average degree="+ (local ? (avgdeg+2) : avgdeg)  +")");
	
	for(int i=0; i<nodes.size(); i++) {
	    SimpleNode src = nodes.get(i);
	    for(int j=0; j<degree; j++) {

		boolean found = false;
		while(!found) {
		    double r = rand.nextFloat();
                    int d = (int) Math.floor (Math.exp (r*Math.log (nodes.size()/2.0)));
		    int destpos;

		    if (rand.nextFloat()<0.5) {
			destpos = i - d;
		    } else {
			destpos = i + d;
		    }

		    if (destpos>=nodes.size()) {
			destpos = destpos - nodes.size();
		    } else if (destpos<0) {
			destpos = destpos + nodes.size();
		    }

		    SimpleNode dst = nodes.get(destpos);

		    if(delays)
			delay = sampleDelay();
		    else
			delay = 1;

		    if (directed) {
			if (inconst) {
			    if (!dst.hasNeighbor(src)) {
				found = true;
				SimpleLink sl = new SimpleLink(src,dst,delay);
				dst.connect(src,sl,SimplePeer.t_DARKNET);
			    }
			} else {
			    if (!src.hasNeighbor(dst)) {
				found = true;
				SimpleLink sl = new SimpleLink(dst,src,delay);
				src.connect(dst,sl,SimplePeer.t_DARKNET);
			    }
			}
		    } else {
			if (!src.hasNeighbor(dst) && !dst.hasNeighbor(src)) {
			    found = true;
			    SimpleLink sl = new SimpleLink(src,dst,delay);
			    src.connect(dst,sl,SimplePeer.t_DARKNET);
			    dst.connect(src,sl,SimplePeer.t_DARKNET);
			}
		    }		    		    
		}
	    }	    
	}
    }        


    /*
     * Evaluate swap directly without no delay
     *
     * @randsteps: steps for random walk from source to dest (0=uniform selection)
     * @randomize_interval: if to enable position randomization with a period of swaps
     */
    public void tryswap(int randsteps, int randomize_interval) {
	
	int i,j;
	SimpleNode na=null,nb=null;

	if (randsteps==0) {
	    do {
		i = rand.nextInt(nodes.size());
		j = rand.nextInt(nodes.size());
	    } while (i==j);

	    na = nodes.get(i);
	    nb = nodes.get(j);
	} else {
	    // Fixme: let the node initialize the request itself
	    do {
		i = rand.nextInt(nodes.size());
		na = nodes.get(i);
		int s = randsteps;
		nb = na;
		while (s > 0) {
		    nb = nb.neighbors.get(rand.nextInt(nb.neighbors.size())).n;
		    s--;
		}
	    } while (na==nb);

	}

	if (randomize_interval > 0) {
	    if (na.rand_interval > 0) {
		na.rand_interval--;
	    } else {
		na.setLocation(rand.nextDouble());
		na.rand_interval = na.rand_interval + randomize_interval;
	    }
	}

	double p = Math.exp(na.logDists(na.getLocation())+nb.logDists(nb.getLocation())-na.logDists(nb.getLocation())-nb.logDists(na.getLocation()));

	// Evaluate step
	if (rand.nextDouble()<p) {
	    double tmp = na.getLocation();
	    na.setLocation(nb.getLocation());
	    nb.setLocation(tmp);	    
	}
    }

    /*
     * Print nodes and edges between them
     *
     * @stdout: if stdout!= 0 we print to stdout, if stdout==0 print to stderr
     */
    public void printNodes(int stdout) {
        for(int i=0; i<nodes.size(); i++) {
            int start = (int) Math.round(nodes.size()*nodes.get(i).getLocation());
            Vector<SimplePeer> neighbors = nodes.get(i).neighbors;

            for (SimplePeer peer: nodes.get(i).neighbors) {
                int stop = (int) Math.round(nodes.size()*peer.n.getLocation());

                if(stdout==1) {
                    System.out.println(start + "\t" + stop);
                } else {
                    System.err.println(start + "\t" + stop);
                }
            }
        }
    }

    /*
     * Print only locations for nodes (in topology order)
     *
     * @stdout: if stdout!= 0 we print to stdout, if stdout==0 print to stderr
     */
    public void printLocations(int stdout) {
	for(SimpleNode n: nodes) {
	    ((stdout==0) ? System.err : System.out).println(n.getLocation());
	}
    }
    
    /*
     * Enable all nodes for learning route delays
     */
    public void learning() {
	for (SimpleNode n: nodes) {
	    n.learning = true;
	}
    }

    /*
     * Disable learning of route delays
     */
    public void unlearning() {
	for (SimpleNode n: nodes) {
	    n.learning = false;
	}
    }

    /*
     * Use delays for generating edges
     */
    public void edgedelays() {
	this.delays = true;
    }
    
    /*
     * Enable delay usage for nodes in routing
     */
    public void usedelays() {
	for (SimpleNode n: nodes) {
	    n.delays = true;
	}
    }
    
    /*
     * Disable delays usage for nodes in routing
     */
    public void nodelays() {
	for (SimpleNode n: nodes) {
	    n.delays = false;
	}
    }

}
